package moe.shizuku.manager.utils

import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * Opt-in escape hatch for Android 12's phantom-process reaper.
 *
 * Android treats a process started by shell (including the Pigu server) as a phantom process and
 * may kill it when the device is under pressure. The documented workaround is a global setting;
 * it is not a per-app exemption and it does not override an OEM's own process manager. Every
 * change is verified by reading the setting back, and disabling the option restores the default
 * by deleting the key instead of writing another value.
 */
object PhantomProcessControl {

    private const val KEY = "settings_enable_monitor_phantom_procs"
    private const val GET = "settings get global $KEY"
    private const val DISABLE = "settings put global $KEY false"
    private const val RESTORE = "settings delete global $KEY"

    enum class State {
        /** The key is absent, so Android uses its default (monitoring enabled). */
        DEFAULT,
        /** The user has disabled phantom-process monitoring. */
        DISABLED,
        UNKNOWN,
        UNSUPPORTED,
    }

    sealed class Result {
        object Success : Result()
        data class Failure(val message: String) : Result()
    }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    suspend fun readState(): State = withContext(Dispatchers.IO) {
        if (!isSupported()) return@withContext State.UNSUPPORTED
        if (!Shizuku.pingBinder()) return@withContext State.UNKNOWN
        when (run(GET).trim().lowercase()) {
            "0", "false" -> State.DISABLED
            "1", "true", "null", "" -> State.DEFAULT
            else -> State.UNKNOWN
        }
    }

    /**
     * @param prevent true to turn the reaper off, false to restore Android's default.
     */
    suspend fun setPreventionEnabled(prevent: Boolean): Result = withContext(Dispatchers.IO) {
        if (!isSupported()) {
            return@withContext Result.Failure("Phantom-process control requires Android 12 or newer")
        }
        if (!Shizuku.pingBinder()) {
            return@withContext Result.Failure("Pigu is not running")
        }

        val expected = if (prevent) State.DISABLED else State.DEFAULT
        val output = run(if (prevent) DISABLE else RESTORE)
        val actual = when (run(GET).trim().lowercase()) {
            "0", "false" -> State.DISABLED
            "1", "true", "null", "" -> State.DEFAULT
            else -> State.UNKNOWN
        }

        if (actual == expected) {
            Result.Success
        } else {
            val detail = output.trim().ifBlank { "the device did not accept the setting" }
            Result.Failure(detail.take(300))
        }
    }

    private fun run(command: String): String {
        val binder = Shizuku.getBinder() ?: return ""
        val service = IShizukuService.Stub.asInterface(binder)
        var process: IRemoteProcess? = null
        return try {
            val p = service.newProcess(arrayOf("sh", "-c", command), null, null) ?: return ""
            process = p
            val output = ParcelFileDescriptor.AutoCloseInputStream(p.inputStream)
                .bufferedReader()
                .readText()
            p.waitFor()
            output
        } catch (tr: Throwable) {
            tr.message ?: tr.javaClass.simpleName
        } finally {
            runCatching { process?.destroy() }
        }
    }
}
