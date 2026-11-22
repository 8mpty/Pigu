package moe.shizuku.manager.management

import android.content.Context
import moe.shizuku.manager.audit.AuditLog
import moe.shizuku.manager.authorization.AuthorizationManager

/**
 * Applies grant/revoke changes and records exactly one audit entry per user action.
 *
 * Keeping this in one place matters because the audit log should describe what the user did, not
 * how many binder calls the UI happened to make. In particular a "grant all" is one entry, not
 * one entry per app.
 */
object AccessActions {

    data class BulkResult(
        val total: Int,
        val succeeded: Int,
        val failed: Int,
    )

    fun setGranted(
        context: Context,
        packageName: String,
        uid: Int,
        granted: Boolean,
        detail: String? = null,
    ): Throwable? {
        return try {
            if (granted) {
                AuthorizationManager.grant(packageName, uid)
            } else {
                AuthorizationManager.revoke(packageName, uid)
            }
            AuditLog.record(
                context,
                if (granted) AuditLog.Action.GRANT else AuditLog.Action.REVOKE,
                AuditLog.Result.OK,
                packageName,
                uid,
                detail,
            )
            null
        } catch (tr: Throwable) {
            AuditLog.record(
                context,
                if (granted) AuditLog.Action.GRANT else AuditLog.Action.REVOKE,
                AuditLog.Result.FAILED,
                packageName,
                uid,
                detail,
            )
            tr
        }
    }

    fun setAll(
        context: Context,
        targets: List<Pair<String, Int>>,
        granted: Boolean,
    ): BulkResult {
        var succeeded = 0
        var failed = 0
        for ((packageName, uid) in targets) {
            try {
                if (granted) {
                    AuthorizationManager.grant(packageName, uid)
                } else {
                    AuthorizationManager.revoke(packageName, uid)
                }
                succeeded++
            } catch (tr: Throwable) {
                failed++
            }
        }

        AuditLog.record(
            context,
            if (granted) AuditLog.Action.GRANT else AuditLog.Action.REVOKE,
            if (failed == 0) AuditLog.Result.OK else AuditLog.Result.FAILED,
            detail = "all; $succeeded ok, $failed failed",
        )
        return BulkResult(targets.size, succeeded, failed)
    }
}
