package moe.shizuku.manager.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.audit.AuditLog
import moe.shizuku.manager.audit.AuditLogSheet
import moe.shizuku.manager.databinding.HomeActivityBinding
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.settings.SettingsActivity
import moe.shizuku.manager.starter.StarterTranscript
import moe.shizuku.manager.utils.CompanionApp
import moe.shizuku.manager.utils.LocalNetworkPermission
import moe.shizuku.manager.utils.PiguDiagnostics
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.SettingsPage
import moe.shizuku.manager.management.AppDiscoverySummary
import moe.shizuku.manager.management.AppsViewModel
import rikka.core.util.ClipboardUtils
import rikka.lifecycle.Status
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.addItemSpacing
import rikka.recyclerview.fixEdgeEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A checklist of the environmental conditions that make Pigu fail to start or stay running.
 * It reuses the same checks as the diagnostics snapshot but adds an action for each problem.
 */
class ServiceDoctorActivity : AppBarActivity() {

    private val homeModel: HomeViewModel by viewModels()
    private val appsModel: AppsViewModel by viewModels()
    private val adapter = ServiceDoctorAdapter()

    private var lastStatus: ServiceStatus = ServiceStatus()
    private var lastSummary: AppDiscoverySummary =
        AppDiscoverySummary(0, 0, 0, false)

    private val batteryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refresh()
        }

    private val localNetworkLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = HomeActivityBinding.inflate(layoutInflater, rootView, true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.service_doctor_title)

        homeModel.serviceStatus.observe(this) {
            if (it.status == Status.SUCCESS) {
                lastStatus = it.data ?: return@observe
                refresh()
            }
        }
        appsModel.summary.observe(this) {
            if (it.status == Status.SUCCESS) {
                lastSummary = it.data ?: return@observe
                refresh()
            }
        }

        val recyclerView = binding.list
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()

        val cardSpacing = resources.getDimension(R.dimen.card_spacing)
        val marginHorizontal = resources.getDimension(R.dimen.margin_horizontal)
        val marginVertical = resources.getDimension(R.dimen.margin_vertical)
        val itemSpacing = cardSpacing / 2f
        recyclerView.addItemSpacing(top = itemSpacing, bottom = itemSpacing)
        recyclerView.addEdgeSpacing(
            top = marginVertical - itemSpacing,
            bottom = marginVertical - itemSpacing,
            left = marginHorizontal,
            right = marginHorizontal,
        )
    }

    override fun onResume() {
        super.onResume()
        homeModel.reload()
        appsModel.load()
        refresh()
    }

    private fun refresh() {
        adapter.submit(buildItems())
    }

    private fun buildItems(): List<ServiceDoctorItem> {
        val items = mutableListOf<ServiceDoctorItem>()
        val running = lastStatus.isRunning

        if (running) {
            items.add(
                ServiceDoctorItem(
                    getString(R.string.service_doctor_service_running),
                    if (lastStatus.uid == 0) {
                        getString(R.string.service_doctor_service_running_root)
                    } else {
                        getString(R.string.service_doctor_service_running_adb, lastStatus.uid)
                    },
                    DoctorLevel.OK,
                )
            )
        } else {
            items.add(
                ServiceDoctorItem(
                    getString(R.string.service_doctor_service_stopped),
                    if (ShizukuSettings.getManuallyStopped()) {
                        getString(R.string.service_doctor_service_manually_stopped)
                    } else {
                        getString(R.string.service_doctor_service_not_running)
                    },
                    if (ShizukuSettings.getManuallyStopped()) DoctorLevel.WARN else DoctorLevel.ERROR,
                    getString(R.string.service_doctor_action_start),
                    onAction = { openStartMethods() },
                )
            )
        }

        val notifications = NotificationManagerCompat.from(this).areNotificationsEnabled()
        items.add(
            ServiceDoctorItem(
                getString(R.string.service_doctor_notifications),
                if (notifications) {
                    getString(R.string.service_doctor_notifications_ok)
                } else {
                    getString(R.string.service_doctor_notifications_blocked)
                },
                if (notifications) DoctorLevel.OK else DoctorLevel.ERROR,
                if (notifications) null else getString(R.string.service_doctor_action_open),
                onAction = if (notifications) null else {
                    { SettingsPage.Notifications.NotificationSettings.launch(this) }
                },
            )
        )

        val batteryIgnored = runCatching {
            SettingsHelper.isIgnoringBatteryOptimizations(this)
        }.getOrDefault(false)
        items.add(
            ServiceDoctorItem(
                getString(R.string.service_doctor_battery),
                if (batteryIgnored) {
                    getString(R.string.service_doctor_battery_ok)
                } else {
                    getString(R.string.service_doctor_battery_optimized)
                },
                if (batteryIgnored) DoctorLevel.OK else DoctorLevel.WARN,
                if (batteryIgnored) null else getString(R.string.service_doctor_action_fix),
                onAction = if (batteryIgnored) null else {
                    {
                        runCatching {
                            batteryLauncher.launch(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .setData(Uri.parse("package:$packageName"))
                            )
                        }
                    }
                },
            )
        )

        if (LocalNetworkPermission.isRequired()) {
            val granted = LocalNetworkPermission.isGranted(this)
            items.add(
                ServiceDoctorItem(
                    getString(R.string.service_doctor_local_network),
                    if (granted) {
                        getString(R.string.service_doctor_local_network_ok)
                    } else {
                        getString(R.string.service_doctor_local_network_denied)
                    },
                    if (granted) DoctorLevel.OK else DoctorLevel.ERROR,
                    if (granted) null else getString(R.string.service_doctor_action_grant),
                    onAction = if (granted) null else {
                        {
                            val permission = LocalNetworkPermission.permissionForSdk()
                            if (permission != null) localNetworkLauncher.launch(permission)
                        }
                    },
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wireless = Settings.Global.getInt(
                contentResolver,
                "adb_wifi_enabled",
                -1,
            ) == 1
            items.add(
                ServiceDoctorItem(
                    getString(R.string.service_doctor_wireless),
                    if (wireless) {
                        getString(R.string.service_doctor_wireless_ok)
                    } else {
                        getString(R.string.service_doctor_wireless_disabled)
                    },
                    if (wireless) DoctorLevel.OK else DoctorLevel.WARN,
                    if (wireless) null else getString(R.string.service_doctor_action_open),
                    onAction = if (wireless) null else {
                        { SettingsHelper.launchOrHighlightWirelessDebugging(this) }
                    },
                )
            )
        }

        val developerOptions = Settings.Global.getInt(
            contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0,
        ) == 1
        val restricted = Build.VERSION.SDK_INT >= 37 ||
            AdbSettings.isDebuggingSettingsRestricted(this)
        items.add(
            ServiceDoctorItem(
                getString(R.string.service_doctor_developer_options),
                when {
                    restricted -> getString(R.string.service_doctor_developer_options_restricted)
                    developerOptions -> getString(R.string.service_doctor_developer_options_ok)
                    else -> getString(R.string.service_doctor_developer_options_disabled)
                },
                if (developerOptions && !restricted) DoctorLevel.OK else DoctorLevel.WARN,
                if (developerOptions && !restricted) null else getString(R.string.service_doctor_action_open),
                onAction = if (developerOptions && !restricted) null else {
                    { SettingsPage.Developer.DeveloperOptions.launch(this) }
                },
            )
        )

        val watchdog = ShizukuSettings.getWatchdog()
        val watchdogRunning = ShizukuSettings.isWatchdogRunning()
        val lastRestart = ShizukuSettings.getLastWatchdogRestart()
        val restartSummary = if (lastRestart > 0L) {
            "\n" + getString(
                R.string.service_doctor_watchdog_last_restart,
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(lastRestart)),
            )
        } else {
            ""
        }
        items.add(
            ServiceDoctorItem(
                getString(R.string.service_doctor_watchdog),
                when {
                    watchdog && watchdogRunning -> getString(R.string.service_doctor_watchdog_ok)
                    watchdog -> getString(R.string.service_doctor_watchdog_not_running)
                    else -> getString(R.string.service_doctor_watchdog_off)
                } + restartSummary,
                if (watchdog && watchdogRunning) DoctorLevel.OK else DoctorLevel.INFO,
                getString(R.string.service_doctor_action_settings),
                onAction = { openSettings() },
            )
        )

        items.add(
            ServiceDoctorItem(
                getString(R.string.service_doctor_phantom),
                if (ShizukuSettings.getPhantomProcessControl()) {
                    getString(R.string.service_doctor_phantom_on)
                } else {
                    getString(R.string.service_doctor_phantom_off)
                },
                DoctorLevel.INFO,
                getString(R.string.service_doctor_action_settings),
                onAction = { openSettings() },
            )
        )

        val companion = CompanionApp.status(this, loadIdentity = true)
        items.add(
            ServiceDoctorItem(
                getString(R.string.service_doctor_companion),
                when {
                    !companion.installed -> getString(R.string.service_doctor_companion_missing)
                    companion.signedByPigu -> getString(R.string.service_doctor_companion_ok)
                    else -> getString(R.string.service_doctor_companion_conflict)
                },
                when {
                    !companion.installed -> DoctorLevel.INFO
                    companion.signedByPigu -> DoctorLevel.OK
                    else -> DoctorLevel.ERROR
                },
                when {
                    !companion.installed -> getString(R.string.service_doctor_action_open)
                    companion.signedByPigu -> null
                    else -> getString(R.string.service_doctor_action_app_info)
                },
                onAction = when {
                    !companion.installed -> {
                        { startActivity(Intent(this, DeveloperActivity::class.java)) }
                    }
                    companion.signedByPigu -> null
                    else -> {
                        { CompanionApp.openAppInfo(this) }
                    }
                },
            )
        )

        val auditEntries = AuditLog.entries(this)
        val deniedCount = auditEntries.count {
            it.action == AuditLog.Action.PERMISSION && it.result == AuditLog.Result.DENIED
        }
        items.add(
            ServiceDoctorItem(
                getString(R.string.service_doctor_access_log),
                if (auditEntries.isEmpty()) {
                    getString(R.string.service_doctor_access_log_empty)
                } else {
                    getString(
                        R.string.service_doctor_access_log_summary,
                        auditEntries.size,
                        deniedCount,
                    )
                },
                if (deniedCount > 0) DoctorLevel.WARN else DoctorLevel.INFO,
                getString(R.string.service_doctor_action_open),
                onAction = { AuditLogSheet.show(this) { refresh() } },
            )
        )

        val lastStart = StarterTranscript.last(this)
        items.add(
            ServiceDoctorItem(
                getString(R.string.service_doctor_last_start),
                if (lastStart == null) {
                    getString(R.string.service_doctor_last_start_none)
                } else {
                    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        .format(Date(lastStart.timestamp))
                    if (lastStart.success) {
                        getString(R.string.service_doctor_last_start_ok, time)
                    } else {
                        getString(
                            R.string.service_doctor_last_start_failed,
                            time,
                            lastStart.message ?: getString(R.string.service_doctor_unknown_error),
                        )
                    }
                },
                when {
                    lastStart == null -> DoctorLevel.INFO
                    lastStart.success -> DoctorLevel.OK
                    else -> DoctorLevel.ERROR
                },
                if (lastStart == null) getString(R.string.service_doctor_action_start)
                else getString(R.string.service_doctor_action_retry),
                onClick = if (lastStart == null) null else {
                    { showTranscript() }
                },
                onAction = { openStartMethods() },
            )
        )

        return items
    }

    private fun openStartMethods() {
        startActivity(Intent(this, StartMethodsActivity::class.java))
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun showTranscript() {
        TranscriptSheet.show(this, StarterTranscript.read(this))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.service_doctor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_copy_diagnostics -> {
                val snapshot = PiguDiagnostics.build(
                    this,
                    lastStatus,
                    lastSummary.authorized,
                    CompanionApp.status(this, loadIdentity = true),
                )
                if (ClipboardUtils.put(this, snapshot) &&
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
                ) {
                    Toast.makeText(this, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT)
                        .show()
                }
                true
            }
            R.id.action_refresh -> {
                homeModel.reload()
                appsModel.load()
                refresh()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
