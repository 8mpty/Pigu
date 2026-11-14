package moe.shizuku.manager.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings

/**
 * The optional companion is a package-identity stub with the official Shizuku package name.
 * It does not provide the service; it only makes apps that hard-code that package name find Pigu.
 */
object CompanionApp {

    const val PACKAGE = "moe.shizuku.privileged.api"

    data class Status(
        val installed: Boolean,
        val versionName: String?,
        val signedByPigu: Boolean,
        val label: String? = null,
        val icon: Drawable? = null
    )

    fun status(context: Context, loadIdentity: Boolean = false): Status {
        val pm = context.packageManager
        val info = try {
            pm.getPackageInfo(PACKAGE, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        if (info == null) return Status(false, null, false)

        val sameSignature = try {
            pm.checkSignatures(context.packageName, PACKAGE) == PackageManager.SIGNATURE_MATCH
        } catch (e: Throwable) {
            false
        }

        val ai = info.applicationInfo
        val label = if (loadIdentity) runCatching { ai?.loadLabel(pm)?.toString() }.getOrNull() else null
        val icon = if (loadIdentity) runCatching { ai?.loadIcon(pm) }.getOrNull() else null

        return Status(true, info.versionName, sameSignature, label, icon)
    }

    fun isInstalled(context: Context): Boolean = status(context).installed

    fun openAppInfo(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
