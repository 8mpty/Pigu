package moe.shizuku.manager.security

import android.content.Context
import android.util.Log
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.audit.AuditLog
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

/**
 * The user-facing global pause / kill switch.
 *
 * Pausing is deliberately stronger than stopping: it stops the service, blocks every start path
 * until resumed, and asks the server to reject clients if a server is still alive. Grants are
 * kept, so resuming restores access without asking the user again.
 */
object AccessPause {

    private const val TAG = "AccessPause"

    /** Returns true when the service was running and was asked to stop. */
    fun pause(context: Context): Boolean {
        val wasRunning = Shizuku.pingBinder() || ShizukuStateMachine.isRunning()
        ShizukuSettings.setPausedWasRunning(wasRunning)
        ShizukuSettings.setAccessPaused(true)

        if (wasRunning) {
            // Best effort: the local pause already blocks starts even if an older server does
            // not understand this transaction.
            GlobalAccess.setPaused(true)
            ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
            runCatching { Shizuku.exit() }
                .onFailure { Log.w(TAG, "Could not stop the service after pausing", it) }
        }
        AuditLog.record(
            context,
            AuditLog.Action.PAUSE,
            AuditLog.Result.OK,
            detail = if (wasRunning) "service stopped" else "start gate",
        )
        return wasRunning
    }

    fun resume(context: Context) {
        val wasRunning = ShizukuSettings.getPausedWasRunning()
        ShizukuSettings.setAccessPaused(false)

        if (Shizuku.pingBinder()) {
            GlobalAccess.setPaused(false)
        }

        // A pause that interrupted a running service should put it back. A pause of an already
        // stopped service should only clear the block, not start something the user did not ask
        // for.
        if (wasRunning && !Shizuku.pingBinder()) {
            ShizukuReceiverStarter.start(context)
        }

        AuditLog.record(
            context,
            AuditLog.Action.RESUME,
            AuditLog.Result.OK,
            detail = if (wasRunning) "service restart requested" else "start gate cleared",
        )
    }
}
