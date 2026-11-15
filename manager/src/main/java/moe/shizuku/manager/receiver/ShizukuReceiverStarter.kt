package moe.shizuku.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.LaunchMethod
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsPage
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.UserHandleCompat
import moe.shizuku.manager.worker.AdbStartWorker
import rikka.shizuku.Shizuku

object ShizukuReceiverStarter {

    private const val TAG = "ShizukuReceiverStarter"

    const val NOTIFICATION_ID = 1447
    private const val NOTIFICATION_ID_UNSUPPORTED = 1449
    private const val NOTIFICATION_ID_DEBUGGING_RESTRICTED = 1451
    private const val CHANNEL_ID = "AdbStartWorker"

    enum class WorkerState {
        AWAITING_WIFI,
        AWAITING_RETRY,
        RUNNING,
        STOPPED
    }

    fun start(context: Context, forceStart: Boolean = false) {
        val storedMode = ShizukuSettings.getLastLaunchMode()
        val mode = resolveLaunchMethod(context, storedMode)
        val binderAlive = Shizuku.pingBinder()

        // Do not trust the cached state machine here. It is driven by binder-death callbacks,
        // which can be delayed while the app process is cached/dozing. A stale RUNNING state
        // makes an automation START intent silently do nothing after the server has died.
        Log.i(
            TAG,
            "start requested: forceStart=$forceStart binderAlive=$binderAlive " +
                "state=${ShizukuStateMachine.get()} storedMode=$storedMode mode=$mode " +
                "user=${UserHandleCompat.myUserId()} tcpPort=${EnvironmentUtils.getAdbTcpPort()} " +
                "tcpMode=${ShizukuSettings.getTcpMode()}"
        )

        if (!forceStart && (UserHandleCompat.myUserId() > 0 || binderAlive)) {
            Log.i(TAG, "start ignored: user not supported or service already running")
            return
        }

        if (mode == LaunchMethod.ROOT) {
            rootStart(context)
        } else if ((mode == LaunchMethod.ADB || mode == LaunchMethod.WIRELESS_ADB) &&
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || EnvironmentUtils.isTelevision() || EnvironmentUtils.getAdbTcpPort() > 0)) {
            if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                AdbStartWorker.enqueue(context)
                updateNotification(context, WorkerState.AWAITING_WIFI)
            } else {
                showPermissionErrorNotification(context)
            }
        } else {
            Log.w(TAG, "Background start not supported: $mode")
            showUnsupportedNotification(context)
        }
    }

    /**
     * A start intent can arrive before Pigu has ever recorded a launch method (for example after
     * a fresh install, cleared data, or a start performed outside the app). In that case, infer
     * the method from the current device state instead of silently refusing to start.
     */
    private fun resolveLaunchMethod(context: Context, storedMode: Int): Int {
        if (storedMode != LaunchMethod.UNKNOWN) return storedMode

        if (EnvironmentUtils.getAdbTcpPort() > 0) {
            Log.i(TAG, "no stored launch method; using ADB because a TCP port is available")
            return LaunchMethod.ADB
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || EnvironmentUtils.isTelevision()) {
            Log.i(TAG, "no stored launch method; using wireless ADB")
            return LaunchMethod.WIRELESS_ADB
        }

        return storedMode
    }

    fun buildNotification(context: Context, msg: String? = null): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wadb_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val cancelIntent = Intent(context, NotifCancelReceiver::class.java)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val attemptNowIntent = Intent(context, NotifAttemptReceiver::class.java)
        val attemptNowPendingIntent = PendingIntent.getBroadcast(
            context, 0, attemptNowIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val restoreIntent = Intent(context, NotifRestoreReceiver::class.java)
        val restorePendingIntent = PendingIntent.getBroadcast(
            context, 0, restoreIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wifiIntent = SettingsPage.InternetPanel.buildIntent(context)
        val wifiPendingIntent = PendingIntent.getActivity(
            context, 0, wifiIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nb = NotificationCompat.Builder(context, CHANNEL_ID)
        
        if (msg != null) nb.setContentText(msg)

        return nb
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(context.getString(R.string.wadb_notification_title))
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_server_restart, context.getString(R.string.wadb_notification_attempt_now), attemptNowPendingIntent)
            .addAction(R.drawable.ic_close_24, context.getString(android.R.string.cancel), cancelPendingIntent)
            .setDeleteIntent(restorePendingIntent)
            .setContentIntent(wifiPendingIntent)
            .build()
    }

    fun updateNotification(context: Context, state: WorkerState) {
        if (state == WorkerState.STOPPED) return
        val msgId = when (state) {
            WorkerState.AWAITING_WIFI -> R.string.wadb_notification_wifi_required
            WorkerState.AWAITING_RETRY -> R.string.wadb_notification_retry
            else -> null
        }
        val msg = if (msgId != null) context.getString(msgId) else null
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(context, msg))
    }

    fun showDebuggingSettingsRestrictedNotification(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wadb_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val developerOptionsIntent = SettingsPage.Developer.DeveloperOptions.buildIntent(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            developerOptionsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(context.getString(R.string.wadb_debugging_settings_restricted_title))
            .setContentText(context.getString(R.string.wadb_debugging_settings_restricted_content))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.wadb_debugging_settings_restricted_content))
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSilent(true)
            .build()

        nm.notify(NOTIFICATION_ID_DEBUGGING_RESTRICTED, notification)
    }

    private fun rootStart(context: Context) {
        if (!Shell.getShell().isRoot) {
            Log.w(TAG, "root start requested, but no root shell is available")
            //NotificationHelper.notify(context, AppConstants.NOTIFICATION_ID_STATUS, AppConstants.NOTIFICATION_CHANNEL_STATUS, R.string.notification_service_start_no_root)
            Shell.getCachedShell()?.close()
            showUnsupportedNotification(context)
            return
        }

        try {
            ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
            Shell.cmd(Starter.internalCommand).exec()
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Failed to start Shizuku with root", e)
            ShizukuStateMachine.update()
        }
    }

    private fun showPermissionErrorNotification(context: Context) {

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wadb_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val webpageIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thedjchi/Shizuku/wiki#shizuku-isnt-starting-on-boot-for-me"))
        val pendingWebpageIntent = PendingIntent.getActivity(
            context, 0, webpageIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val msg = context.getString(R.string.wadb_permission_error_notification_content)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(context.getString(R.string.wadb_permission_error_notification_title))
            .setContentText(msg)
            .setSilent(true)
            .setContentIntent(pendingWebpageIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    /**
     * The start intent (and start-on-boot) can only run when Pigu knows how the service was
     * started before. When that isn't the case, say so instead of failing silently - otherwise
     * automation intents look like they do nothing at all.
     */
    private fun showUnsupportedNotification(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wadb_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingLaunchIntent = PendingIntent.getActivity(
            context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val msg = context.getString(R.string.wadb_unsupported_notification_content)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(context.getString(R.string.wadb_unsupported_notification_title))
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setAutoCancel(true)
            .setContentIntent(pendingLaunchIntent)
            .build()

        nm.notify(NOTIFICATION_ID_UNSUPPORTED, notification)
    }
}