package moe.shizuku.manager.adb

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import java.io.EOFException
import java.net.SocketException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine

object AdbStarter {
    suspend fun startAdb(
        context: Context,
        port: Int,
        switchToTcpMode: Boolean = true,
        log: ((String) -> Unit)? = null,
    ) {
        suspend fun AdbClient.runCommand(cmd: String) {
            command(cmd) { log?.invoke(String(it)) }
        }

        if (ShizukuSettings.getAccessPaused()) {
            throw IllegalStateException("Pigu is paused")
        }

        ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
        log?.invoke("Starting with wireless adb...\n")
        
        try {
            withContext(Dispatchers.IO) {
                val key = runCatching { AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), AdbKey.KEY_NAME) }
                    .getOrElse {
                        if (it is CancellationException) throw it
                        else throw AdbKeyException(it)
                    }

                var activePort = port
                val originalPort = port
                val tcpMode = ShizukuSettings.getTcpMode()
                val tcpPort = ShizukuSettings.getTcpPort()
                if (switchToTcpMode && tcpMode && activePort != tcpPort) {
                    log?.invoke("Connecting on port $activePort...")

                    AdbClient("127.0.0.1", activePort, key).use { client ->
                        client.connect()

                        log?.invoke("Successfully connected on port $activePort...")
                        log?.invoke("\nRestarting in TCP mode port: $tcpPort")

                        runCatching {
                            client.command("tcpip:$tcpPort")
                        }.onFailure { if (it !is EOFException && it !is SocketException) throw it } // Expected when ADB restarts in TCP mode
                    }

                    activePort = tcpPort
                    awaitTcpPort(activePort, log)
                }

                suspend fun startOn(port: Int) {
                    if (ShizukuSettings.getAccessPaused()) {
                        throw IllegalStateException("Pigu is paused")
                    }
                    log?.invoke("Connecting on port $port...")
                    connectWithRetry("127.0.0.1", port, key, log).use { client ->
                        log?.invoke("Successfully connected on port $port...\n")
                        client.runCommand("shell:${Starter.internalCommand}")
                    }
                }

                try {
                    startOn(activePort)
                } catch (e: Exception) {
                    if (activePort == originalPort) throw e

                    // The tcpip switch may have failed before adbd listened on the new port.
                    // The old port is sometimes still alive, and retrying it is cheaper than
                    // making the user pair again.
                    log?.invoke("Could not connect on port $activePort; retrying the original port $originalPort...")
                    startOn(originalPort)
                }
            }
        } catch (e: Throwable) {
            // A failed start must not leave the UI stuck in STARTING forever.
            ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPED)
            throw e
        }
    }

    /**
     * Called by the callers once the binder has (or has not) arrived. Wireless debugging is only
     * kept on after a successful connection and only when the user opted in; all other paths
     * restore the historical behavior of turning it back off.
     */
    fun finishWirelessStart(context: Context, success: Boolean) {
        if (success && ShizukuSettings.getKeepWirelessDebugging()) return

        val wasEnabled = Settings.Global.getInt(
            context.contentResolver,
            "adb_wifi_enabled",
            0
        ) != 0
        if (wasEnabled && !AdbSettings.setWirelessDebuggingEnabled(context, false) &&
            AdbSettings.isDebuggingSettingsRestricted(context)
        ) {
            ShizukuReceiverStarter.showDebuggingSettingsRestrictedNotification(context)
        }
    }

    suspend fun stopTcp(context: Context, port: Int) {
        runCatching {
            val cr = context.contentResolver
            AdbSettings.enableDebugging(context)

            val adbEnabled = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0)
            if (adbEnabled == 0) {
                if (AdbSettings.isDebuggingSettingsRestricted(context)) {
                    throw IllegalStateException(
                        context.getString(R.string.settings_developer_options_dialog_message)
                    )
                }
                throw IllegalStateException("ADB is not enabled")
            }

            ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
            val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), AdbKey.KEY_NAME)
            withContext(Dispatchers.IO) {
                connectWithRetry("127.0.0.1", port, key, null).use { client ->
                    client.command("usb:")
                }
            }
        }.onFailure {
            if (EnvironmentUtils.getAdbTcpPort() > 0) {
                ShizukuStateMachine.update()
                withContext(Dispatchers.Main) {
                    val errorMsg = when (it) {
                        is AdbKeyException -> context.getString(R.string.adb_error_key_store)
                        else -> it.message
                    }
                    Toast.makeText(context, context.getString(R.string.adb_error_stop_tcp) + ". ${errorMsg}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    /**
     * Best-effort check that adbd has actually published the new TCP port. A stale property is
     * not fatal: the connection retries are the real verification, and some devices update the
     * property only after the socket is already accepting connections.
     */
    private suspend fun awaitTcpPort(port: Int, log: ((String) -> Unit)?) {
        repeat(20) {
            if (EnvironmentUtils.getAdbTcpPort() == port) {
                log?.invoke("ADB reports TCP port $port")
                return
            }
            delay(250)
        }
        log?.invoke("ADB did not report TCP port $port yet; trying it anyway")
    }

    private suspend fun connectWithRetry(
        host: String,
        port: Int,
        key: AdbKey,
        log: ((String) -> Unit)?,
    ): AdbClient {
        var last: Exception? = null
        val schedule = PortRetrySchedule.DEFAULT

        for (attempt in 0 until schedule.attempts) {
            delay(schedule.delayBefore(attempt))
            val client = AdbClient(host, port, key)
            try {
                client.connect()
                return client
            } catch (e: Exception) {
                client.close()
                if (e is CancellationException) throw e
                last = e
                if (attempt < schedule.attempts - 1) {
                    log?.invoke(
                        "Port $port not ready (${e.message ?: e.javaClass.simpleName}); retrying..."
                    )
                }
            }
        }

        throw last ?: IllegalStateException("Could not connect to ADB on port $port")
    }
}
