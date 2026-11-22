package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.service.WatchdogService

/**
 * Starts the shared background service after boot when the user opted into accessibility
 * keep-alive. This is separate from [BootCompleteReceiver], which is only enabled for the
 * service's own start-on-boot setting.
 */
class AccessibilityBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ShizukuSettings.getAccessibilityKeepAlive()) return
        if (!ShizukuSettings.getAccessibilityAutoBoot()) return
        if (ShizukuSettings.getPinnedAccessibilityServices().isEmpty()) return

        WatchdogService.start(context.applicationContext)
    }
}
