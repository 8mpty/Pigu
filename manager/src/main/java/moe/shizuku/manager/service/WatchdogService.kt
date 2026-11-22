package moe.shizuku.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.accessibility.AccessibilityControl
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsPage
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

class WatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * A crash gets a restart. A start that keeps failing does not get an unlimited supply of
     * them; see [RestartPolicy].
     */
    private val restartPolicy = RestartPolicy(
        maxAttempts = MAX_RESTART_ATTEMPTS,
        windowMs = RESTART_WINDOW_MS,
        cooldownMs = RESTART_COOLDOWN_MS,
    )

    private var restartInFlight = false
    private var restartStartedAt = 0L
    private var startingSince = 0L
    private var stoppingSince = 0L
    private var gaveUpNotified = false
    private var screenReceiverRegistered = false
    private var accessibilityObserver: ContentObserver? = null

    @Volatile
    private var destroyed = false

    private val accessibilityCheckRunnable = Runnable { checkAccessibilityKeepAlive() }

    private val stateListener: (ShizukuStateMachine.State) -> Unit = { state ->
        when (state) {
            ShizukuStateMachine.State.CRASHED -> {
                // A deliberate stop also ends in binder death. Only a death the user did not ask
                // for is an outage worth reporting and recovering from.
                if (ShizukuSettings.getWatchdog() && !ShizukuSettings.getManuallyStopped()) {
                    showCrashNotification()
                    attemptRestart("crash")
                }
            }

            ShizukuStateMachine.State.RUNNING -> {
                restartInFlight = false
                startingSince = 0L
                stoppingSince = 0L
                restartPolicy.reset()
                gaveUpNotified = false
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIFICATION_ID_GAVE_UP)
            }

            else -> Unit
        }
    }

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                checkServerAndRestartIfDead()
                checkAccessibilityKeepAlive()
            }
        }
    }

    /**
     * Binder-death callbacks can be missed if this process was not alive when the server died.
     * The periodic probe is the backstop for that case; it is one cheap binder ping.
     */
    private val recoveryRunnable = object : Runnable {
        override fun run() {
            if (destroyed) return
            checkServerAndRestartIfDead()
            checkAccessibilityKeepAlive()
            if (!destroyed) handler.postDelayed(this, RECOVERY_POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        destroyed = false
        isRunning.set(true)
        ShizukuStateMachine.addListener(stateListener)

        screenReceiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                this,
                screenOnReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            true
        }.getOrDefault(false)

        accessibilityObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                if (AccessibilityControl.isSelfWrite()) return
                handler.removeCallbacks(accessibilityCheckRunnable)
                handler.postDelayed(accessibilityCheckRunnable, 500L)
            }
        }
        runCatching {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                accessibilityObserver!!,
            )
        }

        handler.postDelayed(recoveryRunnable, BINDER_GRACE_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                // This is the only path that means the user asked to turn the watchdog off.
                ShizukuSettings.setWatchdogPreference(false)
                if (!accessibilityActive()) {
                    stopServiceNow()
                    return START_NOT_STICKY
                }
            }
            ACTION_STOP_ACCESSIBILITY -> {
                ShizukuSettings.setAccessibilityKeepAlive(false)
                ShizukuSettings.setPinnedAccessibilityServices(emptySet())
                if (!ShizukuSettings.getWatchdog()) {
                    stopServiceNow()
                    return START_NOT_STICKY
                }
            }
        }
        destroyed = false
        if (!ShizukuSettings.getWatchdog() && !accessibilityActive()) {
            stopServiceNow()
            return START_NOT_STICKY
        }
        startForegroundCompat(buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(recoveryRunnable)
        handler.removeCallbacks(accessibilityCheckRunnable)
        accessibilityObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        accessibilityObserver = null
        ShizukuStateMachine.removeListener(stateListener)
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenOnReceiver) }
            screenReceiverRegistered = false
        }
        isRunning.set(false)
        // Do not write the preference here. onDestroy also runs when the system or the user swipes
        // the app away; only an explicit stop should disable the watchdog.
        super.onDestroy()
    }

    private fun stopServiceNow() {
        destroyed = true
        handler.removeCallbacks(recoveryRunnable)
        handler.removeCallbacks(accessibilityCheckRunnable)
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_WATCHDOG,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID_WATCHDOG, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * A server that was never started in this boot is not an outage. It is the state the user
     * chose when start-on-boot is off; restarting it here would be a boot start by another name.
     */
    private fun canRecover(): Boolean {
        return ShizukuSettings.getStartOnBoot(this) ||
            ShizukuSettings.wasServerStartedThisBoot(this)
    }

    private fun isUserUnlocked(): Boolean {
        return getSystemService(UserManager::class.java)?.isUserUnlocked != false
    }

    private fun isWirelessStartMethod(): Boolean {
        return when (ShizukuSettings.getLastLaunchMode()) {
            ShizukuSettings.LaunchMethod.WIRELESS_ADB -> true
            ShizukuSettings.LaunchMethod.UNKNOWN -> EnvironmentUtils.getAdbTcpPort() <= 0
            else -> false
        }
    }

    private fun checkAccessibilityKeepAlive() {
        if (destroyed) return
        if (!accessibilityActive()) return
        if (!isUserUnlocked()) return
        val restored = AccessibilityControl.restorePinned(this)
        if (restored.isNotEmpty()) {
            Log.i(TAG, "Restored pinned accessibility services: $restored")
        }
    }

    private fun checkServerAndRestartIfDead() {
        if (destroyed) return
        if (!ShizukuSettings.getWatchdog()) return
        if (ShizukuSettings.getManuallyStopped()) return

        // Observe first: a live binder is worth recording even if this boot did not record a
        // start, and it avoids treating a running server as an outage.
        if (Shizuku.pingBinder()) {
            if (ShizukuStateMachine.get() != ShizukuStateMachine.State.RUNNING) {
                ShizukuStateMachine.update()
            }
            return
        }

        if (!canRecover()) return

        when (ShizukuStateMachine.get()) {
            ShizukuStateMachine.State.STARTING -> {
                // A start can legitimately take a couple of minutes. A STARTING state that
                // outlives that is a failed start that never reported back, not a reason to
                // leave the watchdog disabled forever.
                val now = SystemClock.elapsedRealtime()
                if (startingSince == 0L) startingSince = now
                if (now - startingSince < STARTING_STALE_MS) return
                Log.w(TAG, "Start state is stale; re-checking the binder")
                ShizukuStateMachine.update()
                startingSince = 0L
                if (ShizukuStateMachine.get() == ShizukuStateMachine.State.RUNNING) return
            }
            ShizukuStateMachine.State.STOPPING -> {
                // A stop that never produced binder death is also a failed transition rather
                // than a reason to leave recovery disabled. Manual stops return above.
                val now = SystemClock.elapsedRealtime()
                if (stoppingSince == 0L) stoppingSince = now
                if (now - stoppingSince < STOPPING_STALE_MS) return
                Log.w(TAG, "Stop state is stale; re-checking the binder")
                ShizukuStateMachine.update()
                stoppingSince = 0L
                if (ShizukuStateMachine.get() == ShizukuStateMachine.State.RUNNING) return
            }
            else -> {
                startingSince = 0L
                stoppingSince = 0L
            }
        }

        // Wireless discovery needs the user to be unlocked; the unlock receiver will try again.
        if (!isUserUnlocked() && isWirelessStartMethod()) return

        attemptRestart("probe")
    }

    private fun attemptRestart(reason: String) {
        if (destroyed) return
        if (!ShizukuSettings.getWatchdog()) return
        if (ShizukuSettings.getManuallyStopped()) return
        if (!canRecover()) return
        if (ShizukuReceiverStarter.isStartInFlight()) {
            Log.d(TAG, "A start request is already in flight; not starting another")
            return
        }

        when (ShizukuStateMachine.get()) {
            ShizukuStateMachine.State.STARTING,
            ShizukuStateMachine.State.STOPPING -> return
            else -> Unit
        }

        val now = SystemClock.elapsedRealtime()
        if (restartInFlight && now - restartStartedAt < RESTART_IN_FLIGHT_MS) {
            Log.d(TAG, "Restart already in flight; not starting another")
            return
        }

        when (restartPolicy.decide(now)) {
            RestartPolicy.Decision.COOLDOWN -> return
            RestartPolicy.Decision.GIVE_UP -> {
                showGaveUpNotification()
                return
            }
            RestartPolicy.Decision.ALLOW -> Unit
        }

        restartPolicy.recordAttempt(now)
        ShizukuSettings.setLastWatchdogRestart(System.currentTimeMillis())
        restartInFlight = true
        restartStartedAt = now
        Log.i(TAG, "Restarting the service after $reason")
        ShizukuReceiverStarter.start(applicationContext, forceStart = true)
    }

    private fun buildNotification(): Notification {
        val watchdog = ShizukuSettings.getWatchdog()
        val keepAlive = accessibilityActive()
        val channelId = if (watchdog) WATCHDOG_CHANNEL_ID else ACCESSIBILITY_CHANNEL_ID
        val channelName = if (watchdog) "Watchdog" else "Accessibility keep-alive"

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WatchdogService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAccessibilityIntent = Intent(this, WatchdogService::class.java).apply {
            action = ACTION_STOP_ACCESSIBILITY
        }
        val stopAccessibilityPendingIntent = PendingIntent.getService(
            this, 2, stopAccessibilityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(
                getString(
                    when {
                        watchdog && keepAlive -> R.string.background_running_title
                        watchdog -> R.string.watchdog_running
                        else -> R.string.accessibility_keep_alive_notification_title
                    }
                )
            )
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)

        if (watchdog && keepAlive) {
            builder.setContentText(getString(R.string.background_running_text))
        } else if (keepAlive) {
            builder.setContentText(getString(R.string.accessibility_keep_alive_notification_text))
        }

        if (watchdog) {
            builder.addAction(
                R.drawable.ic_close_24,
                getString(R.string.watchdog_turn_off),
                stopPendingIntent,
            )
        }
        if (keepAlive) {
            builder.addAction(
                R.drawable.ic_close_24,
                getString(R.string.accessibility_keep_alive_turn_off),
                stopAccessibilityPendingIntent,
            )
        }

        return builder.build()
    }

    private fun showCrashNotification() {
        val channelId = CRASH_CHANNEL_ID
        val channelName = "Crash Reports"

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)

        val learnMoreIntent = Intent(Intent.ACTION_VIEW).apply {
            setData(Uri.parse("https://github.com/thedjchi/Shizuku/wiki#shizuku-keeps-stopping-randomly"))
        }
        val learnMorePendingIntent = PendingIntent.getActivity(this, 0, learnMoreIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val disableIntent = SettingsPage.Notifications.NotificationChannel.buildIntent(applicationContext)
        val disablePendingIntent = PendingIntent.getActivity(this, 0, disableIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.watchdog_shizuku_crashed_title))
            .setContentText(getString(R.string.watchdog_shizuku_crashed_text))
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentIntent(learnMorePendingIntent)
            .setAutoCancel(true)
            .addAction(0, getString(R.string.watchdog_shizuku_crashed_action_turn_off_alerts), disablePendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID_CRASH, notification)
    }

    private fun showGaveUpNotification() {
        if (gaveUpNotified) return
        gaveUpNotified = true

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CRASH_CHANNEL_ID,
            "Crash Reports",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CRASH_CHANNEL_ID)
            .setContentTitle(getString(R.string.watchdog_gave_up_title))
            .setContentText(getString(R.string.watchdog_gave_up_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.watchdog_gave_up_text)))
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentIntent(launchPendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID_GAVE_UP, notification)
        // Both describe the same outage; the later, more useful word on it replaces the crash
        // notice instead of stacking under it.
        nm.cancel(NOTIFICATION_ID_CRASH)
    }

    companion object {
        private const val TAG = "ShizukuWatchdog"
        private const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        private const val ACTION_STOP_ACCESSIBILITY = "ACTION_STOP_ACCESSIBILITY"
        private const val NOTIFICATION_ID_WATCHDOG = 1001
        private const val NOTIFICATION_ID_CRASH = 1002
        private const val NOTIFICATION_ID_GAVE_UP = 1003
        const val CRASH_CHANNEL_ID = "crash_reports"
        private const val WATCHDOG_CHANNEL_ID = "shizuku_watchdog"
        private const val ACCESSIBILITY_CHANNEL_ID = "accessibility_keep_alive"

        /** Give the sticky binder a moment to arrive before probing. */
        private const val BINDER_GRACE_MS = 3_000L

        /** One cheap binder ping per minute is the backstop for missed death callbacks. */
        private const val RECOVERY_POLL_MS = 60_000L

        /** A start can legitimately take a couple of minutes (wireless discovery). */
        private const val RESTART_IN_FLIGHT_MS = 180_000L

        /** After this, a STARTING state that never produced a binder is treated as failed. */
        private const val STARTING_STALE_MS = 3 * 60_000L

        /** A stop should finish quickly; after this, re-check rather than skip forever. */
        private const val STOPPING_STALE_MS = 60_000L

        private const val RESTART_COOLDOWN_MS = 90_000L
        private const val RESTART_WINDOW_MS = 15 * 60_000L
        private const val MAX_RESTART_ATTEMPTS = 3

        private val isRunning = AtomicBoolean(false)

        private fun accessibilityActive(): Boolean =
            ShizukuSettings.getAccessibilityKeepAlive() &&
                ShizukuSettings.getPinnedAccessibilityServices().isNotEmpty()

        @JvmStatic
        fun start(context: Context) {
            try {
                val intent = Intent(context, WatchdogService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("ShizukuApplication", "Failed to start WatchdogService: ${e.message}" )
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            context.stopService(Intent(context, WatchdogService::class.java))
        }

        /** Keeps the service alive only while at least one background feature needs it. */
        @JvmStatic
        fun reconcile(context: Context) {
            if (ShizukuSettings.getWatchdog() || accessibilityActive()) {
                start(context)
            } else {
                stop(context)
            }
        }

        @JvmStatic
        fun isRunning(): Boolean = isRunning.get()
    }
}
