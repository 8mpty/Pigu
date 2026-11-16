package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.service.WatchdogService

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val bootCount = runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.BOOT_COUNT,
                -1,
            )
        }.getOrDefault(-1)
        if (BootClaimStore.claim(context, bootCount)) {
            ShizukuReceiverStarter.start(context)
        }
        if (ShizukuSettings.getWatchdog()) WatchdogService.start(context)
    }
}
