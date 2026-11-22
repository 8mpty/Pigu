package moe.shizuku.manager.accessibility

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbPairingAccessibilityService

/**
 * The small, deliberately limited accessibility helper used by the Developer page.
 *
 * Pigu does not turn arbitrary accessibility services on or off. It lists installed services, and
 * it can only keep services that the user explicitly pinned and that were already enabled. This
 * keeps Pigu out of the "silently enable an accessibility service" business, which would be a
 * malware-grade capability.
 */
object AccessibilityControl {

    private const val TAG = "AccessibilityControl"
    private const val SELF_WRITE_WINDOW_MS = 2_000L
    private const val MAX_RESTORES = 3
    private const val RESTORE_WINDOW_MS = 15 * 60_000L

    data class Entry(
        val id: String,
        val label: String,
        val description: String,
        val packageName: String,
        val enabled: Boolean,
        val pinned: Boolean,
    )

    @Volatile
    private var lastSelfWriteAt = 0L
    private var restoreCount = 0
    private var restoreWindowStart = 0L
    private var lastBackoffLogAt = 0L

    fun hasWritePermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun grantCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    /** True for a short window after Pigu writes the setting, so the observer ignores itself. */
    fun isSelfWrite(): Boolean {
        return SystemClock.elapsedRealtime() - lastSelfWriteAt in 0..SELF_WRITE_WINDOW_MS
    }

    /** Services currently listed in the system's enabled-accessibility setting. */
    fun enabledServices(context: Context): Set<ComponentName> {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return emptySet()
        if (raw.isBlank()) return emptySet()
        return raw.split(':')
            .filter { it.isNotBlank() }
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .toSet()
    }

    /**
     * Installed accessibility services, excluding Pigu's own transient pairing service. The list
     * is read-only: pinning is only offered for services that are already enabled.
     */
    fun installedServices(context: Context): List<Entry> {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return emptyList()
        val pm = context.packageManager
        val accessibilityOn = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        val enabled = enabledServices(context).map { it.flattenToString() }.toSet()
        val pinned = ShizukuSettings.getPinnedAccessibilityServices()
        val ownService = AdbPairingAccessibilityService::class.java.name
        val installed = runCatching { manager.installedAccessibilityServiceList }
            .getOrElse { return emptyList() }

        return installed.mapNotNull { info ->
            try {
                val serviceInfo = info.resolveInfo.serviceInfo
                val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
                if (component.packageName == context.packageName && component.className == ownService) {
                    return@mapNotNull null
                }
                val label = runCatching {
                    info.resolveInfo.loadLabel(pm)?.toString()
                }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(component.packageName, 0))
                            .toString()
                    }.getOrNull()
                    ?: component.packageName
                val description = runCatching {
                    info.loadDescription(pm)?.toString()
                }.getOrNull().orEmpty()
                val id = component.flattenToString()
                Entry(
                    id = id,
                    label = label,
                    description = description,
                    packageName = component.packageName,
                    enabled = accessibilityOn && id in enabled,
                    pinned = id in pinned,
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Could not load accessibility service", e)
                null
            }
        }.sortedWith(
            compareByDescending<Entry> { it.enabled }
                .thenBy { it.label.lowercase() }
        )
    }

    /**
     * Re-adds pinned services that disappeared from the enabled list. Returns only after a
     * successful write. Never enables a service that was not explicitly pinned.
     */
    fun restorePinned(context: Context): List<String> {
        if (!ShizukuSettings.getAccessibilityKeepAlive()) return emptyList()
        if (!hasWritePermission(context)) return emptyList()

        val pinned = ShizukuSettings.getPinnedAccessibilityServices()
            .mapNotNull { ComponentName.unflattenFromString(it) }
        if (pinned.isEmpty()) return emptyList()

        val enabled = enabledServices(context).toMutableSet()
        val missing = pinned.filter { it !in enabled }
        if (missing.isEmpty()) {
            restoreCount = 0
            restoreWindowStart = 0L
            return emptyList()
        }

        val now = SystemClock.elapsedRealtime()
        if (now - restoreWindowStart > RESTORE_WINDOW_MS) {
            restoreCount = 0
            restoreWindowStart = now
        }
        if (restoreCount >= MAX_RESTORES) {
            if (now - lastBackoffLogAt > 60_000L) {
                lastBackoffLogAt = now
                Log.w(TAG, "Keeping accessibility services alive is backing off after repeated restores")
            }
            return emptyList()
        }

        enabled.addAll(missing)
        if (!writeServices(context, enabled)) return emptyList()

        restoreCount++
        if (restoreWindowStart == 0L) restoreWindowStart = now
        return missing.map { it.flattenToString() }
    }

    private fun writeServices(context: Context, services: Set<ComponentName>): Boolean {
        val value = services.joinToString(":") { it.flattenToString() }
        return try {
            lastSelfWriteAt = SystemClock.elapsedRealtime()
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                value,
            )
            // Writing the list alone is not enough when this is the first enabled service.
            if (Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0,
                ) != 1
            ) {
                lastSelfWriteAt = SystemClock.elapsedRealtime()
                Settings.Secure.putInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    1,
                )
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore accessibility services", e)
            false
        }
    }

    /** A user pin/unpin is a fresh intent; let the restore policy try again. */
    fun resetBackoff() {
        restoreCount = 0
        restoreWindowStart = 0L
    }
}
