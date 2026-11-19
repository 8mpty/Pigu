package moe.shizuku.manager.home

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbPairingService
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.app.SnackbarHelper
import moe.shizuku.manager.databinding.HomeActivityBinding
import moe.shizuku.manager.management.AppsViewModel
import moe.shizuku.manager.settings.SettingsActivity
import moe.shizuku.manager.security.AccessPause
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.core.ktx.unsafeLazy
import rikka.lifecycle.Status
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.addItemSpacing
import rikka.recyclerview.fixEdgeEffect

abstract class HomeActivity : AppBarActivity() {

    private val homeModel: HomeViewModel by viewModels()
    private val appsModel: AppsViewModel by viewModels()
    private val adapter by unsafeLazy { HomeAdapter(homeModel, appsModel) }

    private val stateListener: (ShizukuStateMachine.State) -> Unit = {
        // Binder callbacks are not guaranteed to arrive on the main thread. Never touch the
        // RecyclerView directly from them; post the refresh so it cannot be dropped or delayed.
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                adapter.updateData()
            }
        }
        if (ShizukuStateMachine.isRunning()) {
            checkServerStatus()
            appsModel.load()
        } else if (ShizukuStateMachine.isDead()) {
            checkServerStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = HomeActivityBinding.inflate(layoutInflater, rootView, true)
        supportActionBar?.title = getString(R.string.app_name)

        homeModel.serviceStatus.observe(this) {
            if (it.status == Status.SUCCESS) {
                val status = homeModel.serviceStatus.value?.data ?: return@observe

                if (status.isRunning) {
                    val mode = when {
                        status.uid == 0 -> ShizukuSettings.LaunchMethod.ROOT
                        ShizukuSettings.getLastLaunchMode() == ShizukuSettings.LaunchMethod.WIRELESS_ADB ->
                            ShizukuSettings.LaunchMethod.WIRELESS_ADB
                        else -> ShizukuSettings.LaunchMethod.ADB
                    }
                    ShizukuSettings.setLastLaunchMode(mode)
                } else if (ShizukuStateMachine.get() != ShizukuStateMachine.State.STARTING) {
                    ShizukuSettings.setLastLaunchMode(ShizukuSettings.LaunchMethod.UNKNOWN)
                }

                adapter.updateData()
            }
        }

        homeModel.shouldShowRebootDialog.observe(this) { shouldShow ->
            if (shouldShow) showExitDialog(
                getString(R.string.home_dialog_reboot_required_title),
                getString(R.string.home_dialog_reboot_required_message)
            )
        }

        homeModel.shouldShowUninstallDialog.observe(this) { shouldShow ->
            if (shouldShow) showExitDialog(
                getString(R.string.home_dialog_duplicate_app_detected_title),
                getString(R.string.home_dialog_duplicate_app_detected_message)
            )
        }

        homeModel.shouldShowBatteryOptimizationSnackbar.observe(this) { shouldShow ->
            if (shouldShow) SnackbarHelper.show(
                this,
                binding.root,
                msg = getString(R.string.snackbar_battery_optimization_home),
                duration = Snackbar.LENGTH_INDEFINITE,
                actionText = getString(R.string.snackbar_action_fix),
                action = { SettingsHelper.requestIgnoreBatteryOptimizations(this, null) }
            )
        }
        homeModel.checkBatteryOptimization()

        appsModel.packages.observe(this) {
            if (it.status == Status.SUCCESS) adapter.updateData()
        }
        appsModel.grantedCount.observe(this) {
            if (it.status == Status.SUCCESS) adapter.updateData()
        }

        val recyclerView = binding.list
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()

        val cardSpacing = resources.getDimension(R.dimen.card_spacing)
        val marginHorizontal = resources.getDimension(R.dimen.margin_horizontal)
        val marginVertical = resources.getDimension(R.dimen.margin_vertical)

        val itemSpacing = cardSpacing / 2f
        val edgeSpacingH = marginHorizontal
        val edgeSpacingV = marginVertical - itemSpacing

        recyclerView.addItemSpacing(top = itemSpacing, bottom = itemSpacing)
        recyclerView.addEdgeSpacing(top = edgeSpacingV, bottom = edgeSpacingV, left = edgeSpacingH, right = edgeSpacingH)

        ShizukuStateMachine.addListener(stateListener)

        if (savedInstanceState == null) {
            handleShortcutIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)

        intent?.let {
            val showDialog = it.getBooleanExtra(HomeActivity.EXTRA_SHOW_PAIRING_DIALOG, false)
            if (showDialog) showAccessibilityDialog()

            val startWadb = it.getBooleanExtra(HomeActivity.EXTRA_START_SERVICE_VIA_WADB, false)
            if (startWadb) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(AdbPairingService.NOTIFICATION_ID)
                StartMethods.startWireless(this, lifecycleScope)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reconcile a stale STOPPED state in case the binder callback arrived while the activity
        // was stopped. Do not override an in-flight Starting/Stopping transition.
        if (ShizukuStateMachine.get() == ShizukuStateMachine.State.STOPPED) {
            ShizukuStateMachine.update()
        }
        adapter.updateData()
        checkServerStatus()
        appsModel.load()
    }

    override fun onPause() {
        super.onPause()
        SnackbarHelper.dismiss()
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                intent.action = null
                startFromShortcut()
            }
            ACTION_PAUSE_ACCESS -> {
                intent.action = null
                confirmPauseFromShortcut()
            }
        }
    }

    private fun startFromShortcut() {
        if (ShizukuSettings.getAccessPaused()) {
            Toast.makeText(this, R.string.access_pause_start_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        if (ShizukuStateMachine.isRunning()) {
            Toast.makeText(this, R.string.shortcut_service_already_running, Toast.LENGTH_SHORT).show()
            return
        }

        val root = ShizukuSettings.getLastLaunchMode() == ShizukuSettings.LaunchMethod.ROOT &&
            EnvironmentUtils.isRooted()
        if (root) {
            StartMethods.startRoot(this)
        } else {
            StartMethods.startWireless(this, lifecycleScope)
        }
    }

    private fun confirmPauseFromShortcut() {
        if (ShizukuSettings.getAccessPaused()) {
            Toast.makeText(this, R.string.settings_access_paused_already, Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_access_paused_dialog_title)
            .setMessage(R.string.settings_access_paused_dialog_message)
            .setPositiveButton(R.string.settings_access_paused_enable) { _, _ ->
                AccessPause.pause(this)
                homeModel.reload()
                adapter.updateData()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showExitDialog(title: String, message: String) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.home_dialog_button_exit, null)
            .setOnDismissListener {
                this.finishAffinity()
            }
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun checkServerStatus() {
        homeModel.reload()
    }

    override fun onDestroy() {
        ShizukuStateMachine.removeListener(stateListener)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_developer -> {
                startActivity(Intent(this, DeveloperActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val ACTION_START_SERVICE = "com.empty.pigu.action.START_SERVICE"
        const val ACTION_PAUSE_ACCESS = "com.empty.pigu.action.PAUSE_ACCESS"

        const val EXTRA_SHOW_PAIRING_DIALOG = "show_pairing_dialog"
        const val EXTRA_START_SERVICE_VIA_WADB = "start_service_via_wadb"
    }

}
