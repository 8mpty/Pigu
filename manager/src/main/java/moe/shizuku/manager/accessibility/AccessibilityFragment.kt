package moe.shizuku.manager.accessibility

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.service.WatchdogService
import rikka.core.util.ClipboardUtils

/**
 * Read-only list of accessibility services, with an opt-in pinning layer.
 *
 * Pigu never enables or disables a service here. A service must already be enabled in Android's
 * accessibility settings before it can be pinned, and pinning always shows the screen/input access
 * warning first.
 */
class AccessibilityFragment : PreferenceFragmentCompat() {

    private lateinit var keepAlive: SwitchPreferenceCompat
    private lateinit var autoBoot: SwitchPreferenceCompat
    private lateinit var permission: Preference
    private lateinit var servicesCategory: PreferenceCategory
    private var refreshing = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()
        val screen = preferenceManager.createPreferenceScreen(context)
        preferenceScreen = screen

        screen.addPreference(
            Preference(context).apply {
                key = "accessibility_intro"
                isPersistent = false
                summary = getString(R.string.accessibility_services_intro)
            }
        )

        val keepCategory = PreferenceCategory(context).apply {
            title = getString(R.string.accessibility_keep_alive_category)
        }
        screen.addPreference(keepCategory)

        keepAlive = SwitchPreferenceCompat(context).apply {
            key = "accessibility_keep_alive"
            isPersistent = false
            title = getString(R.string.accessibility_keep_alive_title)
            summary = getString(R.string.accessibility_keep_alive_summary)
            setOnPreferenceChangeListener { _, value ->
                val enabled = value as? Boolean ?: return@setOnPreferenceChangeListener false
                if (enabled && !AccessibilityControl.hasWritePermission(context)) {
                    showPermissionHint()
                    false
                } else {
                    ShizukuSettings.setAccessibilityKeepAlive(enabled)
                    WatchdogService.reconcile(context)
                    refresh()
                    true
                }
            }
        }
        keepCategory.addPreference(keepAlive)

        autoBoot = SwitchPreferenceCompat(context).apply {
            key = "accessibility_auto_boot"
            isPersistent = false
            title = getString(R.string.accessibility_auto_boot_title)
            summary = getString(R.string.accessibility_auto_boot_summary)
            setOnPreferenceChangeListener { _, value ->
                ShizukuSettings.setAccessibilityAutoBoot(value as? Boolean ?: false)
                true
            }
        }
        keepCategory.addPreference(autoBoot)

        permission = Preference(context).apply {
            key = "accessibility_permission"
            isPersistent = false
            title = getString(R.string.accessibility_permission_title)
            setOnPreferenceClickListener {
                val command = AccessibilityControl.grantCommand(context)
                ClipboardUtils.put(context, command)
                Toast.makeText(
                    context,
                    R.string.accessibility_permission_copied,
                    Toast.LENGTH_SHORT,
                ).show()
                true
            }
        }
        keepCategory.addPreference(permission)

        keepCategory.addPreference(
            Preference(context).apply {
                key = "accessibility_system_settings"
                isPersistent = false
                title = getString(R.string.accessibility_open_system_settings)
                setOnPreferenceClickListener {
                    openSystemSettings()
                    true
                }
            }
        )

        servicesCategory = PreferenceCategory(context).apply {
            title = getString(R.string.accessibility_services_category)
        }
        screen.addPreference(servicesCategory)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (refreshing || !isAdded) return
        refreshing = true
        try {
            val context = requireContext()
            val canWrite = AccessibilityControl.hasWritePermission(context)

            keepAlive.isChecked = ShizukuSettings.getAccessibilityKeepAlive()
            keepAlive.isEnabled = canWrite
            autoBoot.isChecked = ShizukuSettings.getAccessibilityAutoBoot()
            autoBoot.isEnabled = canWrite && keepAlive.isChecked
            permission.isVisible = !canWrite
            permission.summary = getString(R.string.accessibility_permission_missing) +
                "\n" + AccessibilityControl.grantCommand(context)

            servicesCategory.removeAll()

            val services = AccessibilityControl.installedServices(context)
            if (services.isEmpty()) {
                servicesCategory.addPreference(
                    Preference(context).apply {
                        key = "accessibility_empty"
                        isPersistent = false
                        summary = getString(R.string.accessibility_services_empty)
                    }
                )
                return
            }

            services.forEach { entry ->
                servicesCategory.addPreference(servicePreference(entry, canWrite))
            }
        } finally {
            refreshing = false
        }
    }

    private fun servicePreference(
        entry: AccessibilityControl.Entry,
        canWrite: Boolean,
    ): Preference {
        val context = requireContext()
        val icon = runCatching {
            context.packageManager.getApplicationIcon(entry.packageName)
        }.getOrNull()

        if (!entry.enabled && !entry.pinned) {
            return Preference(context).apply {
                key = "accessibility_service:${entry.id}"
                isPersistent = false
                title = entry.label
                summary = getString(R.string.accessibility_service_disabled)
                this.icon = icon
                setOnPreferenceClickListener {
                    openSystemSettings()
                    true
                }
            }
        }

        return SwitchPreferenceCompat(context).apply {
            key = "accessibility_service:${entry.id}"
            isPersistent = false
            title = entry.label
            summary = buildString {
                append(entry.description.ifBlank { entry.packageName })
                append('\n')
                append(
                    getString(
                        when {
                            !entry.enabled -> R.string.accessibility_service_disabled_pinned
                            entry.pinned -> R.string.accessibility_service_pinned
                            else -> R.string.accessibility_service_enabled
                        }
                    )
                )
            }
            this.icon = icon
            isChecked = entry.pinned
            isEnabled = canWrite
            setOnPreferenceChangeListener { _, value ->
                val pin = value as? Boolean ?: return@setOnPreferenceChangeListener false
                if (pin) {
                    confirmPin(entry)
                } else {
                    unpin(entry)
                }
                false
            }
        }
    }

    private fun confirmPin(entry: AccessibilityControl.Entry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.accessibility_pin_confirm_title, entry.label))
            .setMessage(R.string.accessibility_pin_confirm_message)
            .setPositiveButton(R.string.accessibility_pin_confirm_action) { _, _ ->
                if (!ShizukuSettings.getAccessibilityKeepAlive()) {
                    ShizukuSettings.setAccessibilityKeepAlive(true)
                }
                val pinned = ShizukuSettings.getPinnedAccessibilityServices().toMutableSet()
                pinned.add(entry.id)
                ShizukuSettings.setPinnedAccessibilityServices(pinned)
                AccessibilityControl.resetBackoff()
                WatchdogService.reconcile(requireContext())
                refresh()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> refresh() }
            .show()
    }

    private fun unpin(entry: AccessibilityControl.Entry) {
        val pinned = ShizukuSettings.getPinnedAccessibilityServices().toMutableSet()
        pinned.remove(entry.id)
        ShizukuSettings.setPinnedAccessibilityServices(pinned)
        AccessibilityControl.resetBackoff()
        // Do not leave an empty daemon running after the last pin is removed.
        if (pinned.isEmpty()) {
            ShizukuSettings.setAccessibilityKeepAlive(false)
        }
        WatchdogService.reconcile(requireContext())
        refresh()
    }

    private fun showPermissionHint() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.accessibility_permission_title)
            .setMessage(
                getString(R.string.accessibility_permission_missing) +
                    "\n\n" + AccessibilityControl.grantCommand(requireContext())
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun openSystemSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
