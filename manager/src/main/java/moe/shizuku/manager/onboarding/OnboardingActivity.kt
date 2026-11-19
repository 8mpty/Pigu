package moe.shizuku.manager.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.ActivityOnboardingBinding
import moe.shizuku.manager.home.DeveloperActivity
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.LocalNetworkPermission
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.SettingsPage

/**
 * A short, skippable first-run checklist. Permissions are requested here up front, but every
 * runtime path still asks for what it needs later, so skipping this screen never leaves the app
 * silently broken.
 */
class OnboardingActivity : AppBarActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var showingPermissions = true
    private var notificationRequested = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationRequested = true
            refreshPermissions()
            if (!it) {
                Toast.makeText(this, R.string.onboarding_notifications_blocked_hint, Toast.LENGTH_LONG).show()
            }
        }

    private val batteryOptimizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOnboardingBinding.inflate(layoutInflater, rootView, true)
        supportActionBar?.title = getString(R.string.onboarding_app_bar_title)

        binding.allowNotifications.setOnClickListener {
            if (notificationsBlockedByPermission() && !notificationRequested) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                SettingsPage.Notifications.NotificationSettings.launch(this)
            }
        }

        binding.allowLocalNetwork.setOnClickListener {
            LocalNetworkPermission.request(this) { refreshPermissions() }
        }

        binding.allowBattery.setOnClickListener {
            runCatching {
                SettingsHelper.requestIgnoreBatteryOptimizations(this, batteryOptimizationLauncher)
            }.onFailure {
                Toast.makeText(this, R.string.onboarding_permission_open_failed, Toast.LENGTH_LONG).show()
            }
        }

        binding.openDeveloper.setOnClickListener {
            startActivity(Intent(this, DeveloperActivity::class.java))
        }

        binding.skip.setOnClickListener { finishOnboarding() }
        binding.back.setOnClickListener { showPermissions() }
        binding.next.setOnClickListener {
            if (showingPermissions) showStartMethods() else finishOnboarding()
        }

        val actions = binding.onboardingActions
        val baseLeft = actions.paddingLeft
        val baseTop = actions.paddingTop
        val baseRight = actions.paddingRight
        val baseBottom = actions.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(actions) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = baseLeft + bars.left,
                top = baseTop,
                right = baseRight + bars.right,
                bottom = baseBottom + bars.bottom,
            )
            insets
        }

        binding.localNetworkCard.isVisible = LocalNetworkPermission.isRequired()
        if (!EnvironmentUtils.isRooted()) {
            binding.startRootSummary.setText(R.string.onboarding_start_root_unavailable)
        }

        showPermissions()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!showingPermissions) showPermissions() else finishOnboarding()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun showPermissions() {
        showingPermissions = true
        binding.permissionsPage.isVisible = true
        binding.startPage.isVisible = false
        binding.back.isVisible = false
        binding.skip.isVisible = true
        binding.next.setText(R.string.onboarding_continue)
        binding.onboardingTitle.setText(R.string.onboarding_title)
        binding.onboardingIntro.setText(R.string.onboarding_intro)
        refreshPermissions()
        binding.next.post { if (!isFinishing) binding.next.requestFocus() }
    }

    private fun showStartMethods() {
        showingPermissions = false
        binding.permissionsPage.isVisible = false
        binding.startPage.isVisible = true
        binding.back.isVisible = true
        binding.skip.isVisible = false
        binding.next.setText(R.string.onboarding_done)
        binding.onboardingTitle.setText(R.string.onboarding_start_heading)
        binding.onboardingIntro.setText(R.string.onboarding_start_intro)
        binding.next.post { if (!isFinishing) binding.next.requestFocus() }
    }

    private fun refreshPermissions() {
        if (!::binding.isInitialized) return

        val notifications = NotificationManagerCompat.from(this).areNotificationsEnabled()
        binding.notificationsStatus.setText(
            if (notifications) R.string.onboarding_status_allowed else R.string.onboarding_status_blocked
        )
        binding.allowNotifications.setText(
            if (notifications || notificationRequested) R.string.onboarding_open_settings
            else R.string.onboarding_allow
        )

        if (LocalNetworkPermission.isRequired()) {
            val granted = LocalNetworkPermission.isGranted(this)
            binding.localNetworkStatus.text = getString(
                if (granted) R.string.onboarding_status_allowed else R.string.onboarding_status_blocked
            )
            binding.allowLocalNetwork.isEnabled = !granted
            binding.allowLocalNetwork.setText(
                if (granted) R.string.onboarding_status_allowed else R.string.onboarding_allow
            )
        }

        val battery = runCatching {
            SettingsHelper.isIgnoringBatteryOptimizations(this)
        }.getOrDefault(false)
        binding.batteryStatus.setText(
            if (battery) R.string.onboarding_status_allowed else R.string.onboarding_status_optional
        )
        binding.allowBattery.isEnabled = !battery
        binding.allowBattery.setText(
            if (battery) R.string.onboarding_status_allowed else R.string.onboarding_allow
        )
    }

    private fun notificationsBlockedByPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun finishOnboarding() {
        ShizukuSettings.setOnboardingCompleted(true)
        finish()
    }
}
