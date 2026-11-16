package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

class ManualStopReceiver : AuthenticatedReceiver() {
    override fun onAuthenticated(context: Context, intent: Intent) {
        val applicationId = BuildConfig.APPLICATION_ID
        if (intent.action != "${applicationId}.STOP") return

        // A manifest receiver can start a cold process, where ShizukuStateMachine hasn't seen a
        // binder yet even though the server is alive. Checking the binder directly avoids
        // silently ignoring the stop intent in that case.
        //
        // Record the deliberate stop first: if the binder is already gone, or if it dies while
        // the stop is being delivered, the watchdog must not read that death as a crash.
        ShizukuSettings.setManuallyStopped(true)
        if (!Shizuku.pingBinder()) return

        ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
        runCatching { Shizuku.exit() }
    }
}