package moe.shizuku.manager.home

import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.RootWarning
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

/**
 * Restarting is the supported way to replace a stale server after a manager update. It is not a
 * hot swap: the old binder is stopped first, then the last working start path is launched.
 */
object ServiceRestart {

    fun confirm(activity: FragmentActivity, status: ServiceStatus) {
        if (!ShizukuStateMachine.isRunning()) return

        // The card can be shown before the full ServiceStatus reload finishes. Use the live
        // binder UID in that case so the root/non-root confirmation is still correct.
        val uid = if (status.uid >= 0) status.uid else runCatching { Shizuku.getUid() }.getOrDefault(-1)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_restart_service_title)
            .setMessage(
                if (uid == 0) {
                    R.string.dialog_restart_service_message_root
                } else {
                    R.string.dialog_restart_service_message
                }
            )
            .setPositiveButton(R.string.restart_service) { _, _ ->
                if (uid == 0) {
                    RootWarning.confirm(activity) { restart(activity, root = true) }
                } else {
                    restart(activity, root = false)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun restart(activity: FragmentActivity, root: Boolean) {
        val port = EnvironmentUtils.getAdbTcpPort()
        val discoverWireless = !root && (port <= 0 || EnvironmentUtils.isWifiRequired())

        // Suppress the watchdog between the deliberate stop and the new start request. The
        // Starter screen clears this when it is an explicit user start.
        ShizukuSettings.setManuallyStopped(true)
        ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
        val stopped = runCatching { Shizuku.exit() }.isSuccess
        if (!stopped) {
            // The binder was already gone, or the transaction failed. Do not leave the
            // deliberate-stop flag behind if the old service is still running.
            ShizukuSettings.setManuallyStopped(false)
            ShizukuStateMachine.update()
        }

        val intent = Intent(activity, StarterActivity::class.java).apply {
            when {
                root -> putExtra(StarterActivity.EXTRA_IS_ROOT, true)
                discoverWireless -> putExtra(StarterActivity.EXTRA_DISCOVER_WIRELESS, true)
                else -> putExtra(StarterActivity.EXTRA_PORT, port)
            }
        }
        activity.startActivity(intent)
        Toast.makeText(activity, R.string.service_restart_started, Toast.LENGTH_SHORT).show()
    }
}
