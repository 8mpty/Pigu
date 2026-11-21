package moe.shizuku.manager.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.starter.StarterTranscript
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the text copied by the Developer page diagnostics card.
 *
 * This is meant to be pasted into a bug report, so it must stay useful without containing the
 * automation token, Wi-Fi names, IP addresses, or a list of installed/authorized apps.
 */
object PiguDiagnostics {

    fun build(
        context: Context,
        status: ServiceStatus,
        grantedCount: Int,
        companion: CompanionApp.Status,
    ): String {
        val builder = StringBuilder()
        fun add(label: String, value: Any?) {
            builder.append(label).append(": ").append(value ?: "unknown").append('\n')
        }

        builder.append("Pigu diagnostics\n")
        builder.append("No auth token, Wi-Fi name, IP address, or app list is included.\n\n")

        add("Generated", SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
        add("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        add("Package", context.packageName)
        add("App build", if (BuildConfig.DEBUG) "debug" else "release")
        add("Android", "${oneLine(Build.VERSION.RELEASE)} (API ${Build.VERSION.SDK_INT})")
        add("Target SDK", context.applicationInfo.targetSdkVersion)
        add("Device", deviceName())
        add("Build tags", oneLine(Build.TAGS))
        add("OS build type", oneLine(Build.TYPE))
        add("ABI", Build.SUPPORTED_ABIS.joinToString(", ") { oneLine(it) })

        add("Service state", ShizukuStateMachine.get().name)
        add("Service running", status.isRunning)
        add("Manually stopped", if (ShizukuSettings.getManuallyStopped()) "yes" else "no")
        add("Server started this boot", if (ShizukuSettings.wasServerStartedThisBoot(context)) "yes" else "no")
        if (status.isRunning) {
            add("Service UID", status.uid)
            add("Service API", status.apiVersion)
            add("Service patch", status.patchVersion)
            add("SELinux context", status.seContext?.let(::oneLine)?.takeIf { it.isNotEmpty() } ?: "unknown")
            add("Can grant runtime permissions", status.permission)
        }
        add("Launch method", launchMethodName(ShizukuSettings.getLastLaunchMode()))
        add("Authorized apps", grantedCount)

        add("Start on boot", enabled(ShizukuSettings.getStartOnBoot(context)))
        add("Watchdog preference", enabled(ShizukuSettings.getWatchdog()))
        add("Watchdog service running", ShizukuSettings.isWatchdogRunning())
        add(
            "Last watchdog restart",
            ShizukuSettings.getLastWatchdogRestart().takeIf { it > 0L }?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(it))
            } ?: "never",
        )
        add("Phantom process control", enabled(ShizukuSettings.getPhantomProcessControl()))

        add("USB debugging", globalBoolean(context, Settings.Global.ADB_ENABLED))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add("Wireless debugging", globalBoolean(context, "adb_wifi_enabled"))
        } else {
            add("Wireless debugging", "not supported")
        }
        add("Developer options", globalBoolean(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED))
        add("ADB TCP port", runCatching { EnvironmentUtils.getAdbTcpPort() }.getOrDefault(-1).takeIf { it > 0 } ?: "none")
        add("Configured TCP port", ShizukuSettings.getTcpPort())
        add("TCP mode", enabled(ShizukuSettings.getTcpMode()))

        add("Write secure settings", permission(context, android.Manifest.permission.WRITE_SECURE_SETTINGS))
        val localNetworkPermission = LocalNetworkPermission.permissionForSdk()
        if (localNetworkPermission != null) {
            add("Local network permission", "${LocalNetworkPermission.name()} ($localNetworkPermission)")
            add("Local network access", permission(context, localNetworkPermission))
        } else {
            add("Local network access", "not required")
        }
        add("Notifications", enabled(NotificationManagerCompat.from(context).areNotificationsEnabled()))
        add(
            "Battery optimization",
            when {
                runCatching { SettingsHelper.isIgnoringBatteryOptimizations(context) }.getOrDefault(false) -> "ignored"
                else -> "not ignored"
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            add("Background restricted", activityManager?.isBackgroundRestricted ?: "unknown")
        } else {
            add("Background restricted", "not supported")
        }

        add(
            "Companion app",
            when {
                !companion.installed -> "not installed"
                companion.signedByPigu -> "installed, Pigu signature, version ${companion.versionName ?: "unknown"}"
                else -> "installed, different signature, version ${companion.versionName ?: "unknown"}"
            },
        )
        val lastStart = StarterTranscript.last(context)
        add(
            "Last start",
            if (lastStart == null) {
                "none recorded"
            } else {
                val state = if (lastStart.success) "success" else "failure"
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
                    .format(Date(lastStart.timestamp))
                "$state at $time"
            },
        )
        add(
            "Debuggable",
            enabled((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0),
        )

        return builder.toString()
    }

    private fun permission(context: Context, permission: String): String {
        return if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            "granted"
        } else {
            "not granted"
        }
    }

    private fun globalBoolean(context: Context, key: String): String {
        val value = runCatching {
            Settings.Global.getInt(context.contentResolver, key, -1)
        }.getOrDefault(-1)
        return when (value) {
            1 -> "enabled"
            0 -> "disabled"
            else -> "unknown"
        }
    }

    private fun launchMethodName(mode: Int): String = when (mode) {
        ShizukuSettings.LaunchMethod.ROOT -> "root"
        ShizukuSettings.LaunchMethod.ADB -> "adb"
        ShizukuSettings.LaunchMethod.WIRELESS_ADB -> "wireless debugging"
        else -> "unknown"
    }

    private fun deviceName(): String {
        val name = listOfNotNull(
            oneLine(Build.MANUFACTURER).takeIf { it.isNotEmpty() },
            oneLine(Build.MODEL).takeIf { it.isNotEmpty() },
        ).joinToString(" ")
        return name.ifEmpty { "unknown" }
    }

    private fun enabled(value: Boolean): String = if (value) "enabled" else "disabled"

    private fun oneLine(value: String?): String {
        return value.orEmpty()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
    }
}
