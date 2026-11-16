package moe.shizuku.manager.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.security.AuthTokenGate
import moe.shizuku.manager.security.AuthTokenStatus

abstract class AuthenticatedReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "auth_errors"
        private const val CHANNEL_NAME = "Authentication Errors"
        private const val NOTIFICATION_ID = 1450
        private const val GUARD_PREFERENCES = "auth_notification_guard"
        private const val KEY_LAST_NOTIFICATION = "last_notification_elapsed"
    }

    final override fun onReceive(context: Context, intent: Intent) {
        val providedToken = intent.getStringExtra("auth")
        val expectedToken = ShizukuSettings.getAuthToken()

        when (AuthTokenGate.evaluate(expectedToken, providedToken)) {
            AuthTokenStatus.VALID -> onAuthenticated(context, intent)
            AuthTokenStatus.MISSING -> context.notifyRateLimited(
                R.string.notification_auth_missing_title,
                R.string.notification_auth_missing_message,
            )
            AuthTokenStatus.INVALID -> context.notifyRateLimited(
                R.string.notification_auth_invalid_title,
                R.string.notification_auth_invalid_message,
            )
        }
    }

    /**
     * A wrong token is a local misconfiguration, not an emergency. Keep the first warning, but
     * do not let a sender that keeps retrying turn it into an unbounded stream of notifications.
     */
    private fun Context.notifyRateLimited(title: Int, message: Int) {
        val preferences = getSharedPreferences(GUARD_PREFERENCES, Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val last = preferences.getLong(KEY_LAST_NOTIFICATION, -1L)
        if (!AuthTokenGate.shouldNotify(now, last)) return

        preferences.edit().putLong(KEY_LAST_NOTIFICATION, now).apply()
        notify(title, message)
    }

    private fun Context.notify(title: Int, message: Int) {
        val titleStr = getString(title)
        val messageStr = getString(message)

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titleStr)
            .setContentText(messageStr)
            .setContentIntent(launchPendingIntent)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_system_icon)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    abstract fun onAuthenticated(context: Context, intent: Intent)
}
