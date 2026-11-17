package moe.shizuku.manager.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Android 16 and 17 gate local-network discovery used by wireless ADB behind runtime
 * permissions. Keep the mapping in one place so pairing, discovery and the Service Doctor all
 * ask for the same permission.
 */
object LocalNetworkPermission {

    const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    fun permissionForSdk(): String? = when {
        Build.VERSION.SDK_INT >= 37 -> ACCESS_LOCAL_NETWORK
        Build.VERSION.SDK_INT >= 36 -> Manifest.permission.NEARBY_WIFI_DEVICES
        else -> null
    }

    fun isRequired(): Boolean = permissionForSdk() != null

    fun isGranted(context: Context): Boolean {
        val permission = permissionForSdk() ?: return true
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    fun name(): String = when {
        Build.VERSION.SDK_INT >= 37 -> "Local network"
        Build.VERSION.SDK_INT >= 36 -> "Nearby Wi-Fi devices"
        else -> "Not required"
    }
}
