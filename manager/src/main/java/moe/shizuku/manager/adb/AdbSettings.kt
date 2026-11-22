package moe.shizuku.manager.adb

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Best-effort access to Android's ADB-related global settings.
 *
 * Starting with Android 17 QPR3, changing these settings also requires MANAGE_DEBUGGING, a
 * signature|privileged permission that a normal app cannot hold. WRITE_SECURE_SETTINGS alone is
 * no longer enough, so a failed write must not crash the manager or abort startup. On older
 * builds these calls keep working exactly as before.
 */
object AdbSettings {

    private const val TAG = "AdbSettings"

    private var restricted: Boolean? = null

    fun enableDebugging(context: Context): Boolean {
        val enabled = setAdbEnabled(context, true)
        val timeout = putGlobalSetting(context, "adb_allowed_connection_time", "0")
        return enabled && timeout
    }

    fun setAdbEnabled(context: Context, enabled: Boolean): Boolean =
        putGlobalSetting(context, Settings.Global.ADB_ENABLED, if (enabled) "1" else "0")

    fun setWirelessDebuggingEnabled(context: Context, enabled: Boolean): Boolean =
        putGlobalSetting(context, "adb_wifi_enabled", if (enabled) "1" else "0")

    /**
     * Returns true when this build rejects ADB settings writes even though Pigu has
     * WRITE_SECURE_SETTINGS. The check writes the current value back, so it does not change any
     * setting. It is cached because the answer cannot change while the process is alive.
     */
    fun isDebuggingSettingsRestricted(context: Context): Boolean {
        restricted?.let { return it }

        if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            // Missing WRITE_SECURE_SETTINGS is a separate problem with its own notification.
            return false
        }

        val result = try {
            val current = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            )
            Settings.Global.putString(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                current.toString()
            )
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Android requires MANAGE_DEBUGGING for ADB settings", e)
            true
        }

        restricted = result
        return result
    }

    /**
     * Android 17 (API 37) is the first release that can require MANAGE_DEBUGGING. Keep the
     * Developer options shortcut available there even before Pigu has connected and received
     * WRITE_SECURE_SETTINGS.
     */
    fun shouldShowDeveloperOptionsShortcut(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 37 || isDebuggingSettingsRestricted(context)

    private fun putGlobalSetting(context: Context, name: String, value: String): Boolean {
        if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        return try {
            Settings.Global.putString(context.contentResolver, name, value)
            true
        } catch (e: SecurityException) {
            restricted = true
            Log.w(TAG, "Cannot change $name; debugging must be configured in Developer options", e)
            false
        }
    }
}
