package moe.shizuku.manager.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.home.StartMethodsActivity

/**
 * One place for the local-network permission required by mDNS discovery on Android 16/17.
 *
 * Android 17 (SDK 37) gates local networking behind ACCESS_LOCAL_NETWORK. Android 16 (SDK 36)
 * uses NEARBY_WIFI_DEVICES. Every discovery path must pass through this object: an activity can
 * request the permission and continue, while a worker or service cannot request it and must
 * surface an actionable notification instead.
 */
object LocalNetworkPermission {

    const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    const val NOTIFICATION_ID = 1453
    private const val CHANNEL_ID = "LocalNetworkPermission"
    private const val REQUEST_TAG = "local_network_permission"
    private const val RESULT_KEY = "moe.shizuku.manager.local_network_permission"
    private const val EXTRA_GRANTED = "granted"

    fun permissionForSdk(sdk: Int = Build.VERSION.SDK_INT): String? = when {
        sdk >= 37 -> ACCESS_LOCAL_NETWORK
        sdk >= 36 -> Manifest.permission.NEARBY_WIFI_DEVICES
        else -> null
    }

    fun isRequired(sdk: Int = Build.VERSION.SDK_INT): Boolean = permissionForSdk(sdk) != null

    fun isGranted(context: Context): Boolean {
        val permission = permissionForSdk() ?: return true
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    fun name(sdk: Int = Build.VERSION.SDK_INT): String = when {
        sdk >= 37 -> "Local network"
        sdk >= 36 -> "Nearby Wi-Fi devices"
        else -> "Not required"
    }

    /**
     * Requests the permission when needed and always invokes [onResult] exactly once on the main
     * thread. The callback is delivered through a headless fragment so callers do not have to
     * duplicate the SDK mapping or register their own launcher.
     */
    fun request(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        val permission = permissionForSdk()
        if (permission == null || isGranted(activity)) {
            onResult(true)
            return
        }

        val fragmentManager = activity.supportFragmentManager
        if (fragmentManager.isStateSaved) {
            onResult(false)
            return
        }

        fragmentManager.setFragmentResultListener(RESULT_KEY, activity) { _, result ->
            onResult(result.getBoolean(EXTRA_GRANTED))
        }

        if (fragmentManager.findFragmentByTag(REQUEST_TAG) == null) {
            fragmentManager.beginTransaction()
                .add(RequestFragment(), REQUEST_TAG)
                .commitAllowingStateLoss()
        }
    }

    /**
     * For contexts that cannot show a permission dialog (the ADB worker, services). The
     * notification opens the start screen, where the normal activity gate requests the permission.
     */
    fun showMissingNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.local_network_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )

        val intent = Intent(context, StartMethodsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val message = context.getString(R.string.local_network_notification_message)
        val action = context.getString(R.string.local_network_action_open)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_server_error_24dp)
            .setContentTitle(context.getString(R.string.local_network_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .addAction(0, action, pendingIntent)
            .setAutoCancel(true)
            .setSilent(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    class RequestFragment : Fragment() {

        private val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            complete(it)
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            if (savedInstanceState != null) return

            val permission = permissionForSdk()
            if (permission == null || isGranted(requireContext())) {
                complete(true)
            } else {
                launcher.launch(permission)
            }
        }

        private fun complete(granted: Boolean) {
            val manager = parentFragmentManager
            manager.setFragmentResult(RESULT_KEY, Bundle().apply {
                putBoolean(EXTRA_GRANTED, granted)
            })
            manager.beginTransaction()
                .remove(this)
                .commitAllowingStateLoss()
        }
    }
}
