package moe.shizuku.manager.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.starter.StarterActivity

/**
 * Root mode gives every authorized app the same power as the Pigu server. The first root start
 * explains that once; later starts use the acknowledgement. External root starts (automation,
 * boot) are blocked until the user has seen the explanation, because those paths cannot show a
 * dialog.
 */
object RootWarning {

    fun isAcknowledged(): Boolean = ShizukuSettings.getRootWarningAcknowledged()

    fun markAcknowledged() = ShizukuSettings.setRootWarningAcknowledged(true)

    fun confirm(context: Context, onConfirmed: () -> Unit) {
        if (isAcknowledged()) {
            onConfirmed()
            return
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.root_warning_title)
            .setMessage(R.string.root_warning_message)
            .setPositiveButton(R.string.root_warning_continue) { _, _ ->
                markAcknowledged()
                onConfirmed()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun startRoot(context: Context) {
        if (ShizukuSettings.getAccessPaused()) {
            Toast.makeText(context, R.string.access_pause_start_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        confirm(context) {
            context.startActivity(
                Intent(context, StarterActivity::class.java).apply {
                    putExtra(StarterActivity.EXTRA_IS_ROOT, true)
                }
            )
        }
    }
}
