package moe.shizuku.manager.utils

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.audit.AuditLog
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * Installs the companion APK that is bundled in this app's assets.
 *
 * The companion is optional, so this only runs after an explicit user action. The preferred path
 * pipes the APK through the running Pigu service, which avoids giving the app the "install unknown
 * apps" permission. Root is used when the service isn't running, and the system installer is the
 * last resort (and the option users get when they don't want a privileged install).
 */
object CompanionInstaller {

    private const val ASSET = "companion.apk"
    private const val REMOTE_APK = "/data/local/tmp/pigu-companion.apk"
    private const val NEW_PROCESS_BLOCKED = "NEWPROCESS_NULL"

    sealed class Result {
        object Success : Result()
        object SystemInstallerOpened : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun install(context: Context): Result =
        withContext(Dispatchers.IO) {
            val status = CompanionApp.status(context)
            if (status.installed && !status.signedByPigu) {
                return@withContext Result.Failure(context.getString(R.string.companion_install_conflict))
                    .also {
                        AuditLog.record(
                            context,
                            AuditLog.Action.COMPANION,
                            AuditLog.Result.CONFLICT,
                            detail = "different signature",
                        )
                    }
            }

            val bytes = try {
                context.assets.open(ASSET).use { it.readBytes() }
            } catch (e: Exception) {
                return@withContext Result.Failure(context.getString(R.string.companion_install_asset_missing))
                    .also {
                        AuditLog.record(
                            context,
                            AuditLog.Action.COMPANION,
                            AuditLog.Result.FAILED,
                            detail = "asset missing",
                        )
                    }
            }

            val result = if (Shizuku.pingBinder()) {
                val output = installWithShizuku(bytes)
                when {
                    output.contains("EXIT:0") -> Result.Success
                    output == NEW_PROCESS_BLOCKED -> openSystemInstaller(context, bytes)
                    else -> Result.Failure(output.take(600).ifBlank { "install failed" })
                }
            } else if (Shell.getShell().isRoot) {
                installWithRoot(context, bytes)
            } else {
                openSystemInstaller(context, bytes)
            }
            auditResult(context, result, "in-app")
            result
        }

    suspend fun installWithSystemInstaller(context: Context): Result =
        withContext(Dispatchers.IO) {
            val bytes = try {
                context.assets.open(ASSET).use { it.readBytes() }
            } catch (e: Exception) {
                val result = Result.Failure(context.getString(R.string.companion_install_asset_missing))
                auditResult(context, result, "system installer")
                return@withContext result
            }
            val result = openSystemInstaller(context, bytes)
            auditResult(context, result, "system installer")
            result
        }

    private fun auditResult(context: Context, result: Result, path: String) {
        val auditResult = when (result) {
            is Result.Success, is Result.SystemInstallerOpened -> AuditLog.Result.OK
            is Result.Failure -> AuditLog.Result.FAILED
        }
        val detail = when (result) {
            is Result.Success -> "$path; installed"
            is Result.SystemInstallerOpened -> "$path; installer opened"
            is Result.Failure -> "$path; ${result.message.take(160)}"
        }
        AuditLog.record(context, AuditLog.Action.COMPANION, auditResult, detail = detail)
    }

    @Suppress("DEPRECATION")
    private fun installWithShizuku(bytes: ByteArray): String {
        val script =
            "rm -f $REMOTE_APK; cat > $REMOTE_APK && chmod 644 $REMOTE_APK && pm install -r $REMOTE_APK 2>&1; echo EXIT:\$?; rm -f $REMOTE_APK"

        var process: IRemoteProcess? = null
        return try {
            val binder = Shizuku.getBinder() ?: return "Shizuku isn't running"
            val service = IShizukuService.Stub.asInterface(binder)
            val p = service.newProcess(arrayOf("sh", "-c", script), null, null)
                ?: return NEW_PROCESS_BLOCKED
            process = p
            ParcelFileDescriptor.AutoCloseOutputStream(p.outputStream).use { it.write(bytes) }
            ParcelFileDescriptor.AutoCloseInputStream(p.inputStream)
                .bufferedReader()
                .readText()
                .also { p.waitFor() }
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        } finally {
            runCatching { process?.destroy() }
        }
    }

    private fun installWithRoot(context: Context, bytes: ByteArray): Result {
        val tmp = File(context.cacheDir, "companion_root.apk")
        return try {
            tmp.writeBytes(bytes)
            val result = Shell.cmd("pm install -r '${tmp.absolutePath}' 2>&1; echo EXIT:\$?").exec()
            val output = result.out.joinToString("\n")
            if (output.contains("EXIT:0")) {
                Result.Success
            } else {
                Result.Failure(output.take(600).ifBlank { "install failed" })
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { tmp.delete() }
        }
    }

    private fun openSystemInstaller(context: Context, bytes: ByteArray): Result {
        return try {
            val tmp = File(context.cacheDir, "companion_install.apk")
            tmp.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tmp
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.SystemInstallerOpened
        } catch (e: Exception) {
            Result.Failure(e.message ?: context.getString(R.string.companion_install_no_installer))
        }
    }
}
