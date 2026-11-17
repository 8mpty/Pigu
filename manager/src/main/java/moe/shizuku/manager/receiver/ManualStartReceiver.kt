package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.audit.AuditLog

class ManualStartReceiver : AuthenticatedReceiver() {
    override fun onAuthenticated(context: Context, intent: Intent) {
        val applicationId = BuildConfig.APPLICATION_ID
        if (intent.action != "${applicationId}.START") return

        AuditLog.record(
            context,
            AuditLog.Action.AUTOMATION_START,
            AuditLog.Result.OK,
            detail = "broadcast",
        )
        ShizukuReceiverStarter.start(context, allowMetered = true)
    }
}