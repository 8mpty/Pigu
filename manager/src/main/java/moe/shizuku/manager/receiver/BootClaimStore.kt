package moe.shizuku.manager.receiver

import android.content.Context
import android.os.Build
import android.util.Log
import moe.shizuku.manager.utils.UserHandleCompat

/**
 * Persists the last boot for which start-on-boot already ran.
 *
 * The file is deliberately separate from the normal settings so it is not restored from a backup
 * made on another boot or device.
 */
object BootClaimStore {

    private const val TAG = "PiguBootClaim"
    private const val PREFS_NAME = "boot_claim"
    private const val KEY_LAST_BOOT_PREFIX = "last_boot_count_user_"

    fun claim(context: Context, bootCount: Int): Boolean {
        if (bootCount < 0) return true

        val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
        val preferences = storageContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_LAST_BOOT_PREFIX + UserHandleCompat.myUserId()
        val lastClaimedBootCount = preferences.getInt(key, -1)

        if (!BootClaim.shouldStart(bootCount, lastClaimedBootCount)) {
            Log.i(TAG, "Start-on-boot already handled for boot $bootCount")
            return false
        }

        if (!preferences.edit().putInt(key, bootCount).commit()) {
            // Starting is preferable to silently skipping start-on-boot if storage is unavailable.
            Log.w(TAG, "Could not persist boot claim for boot $bootCount")
        }
        return true
    }
}
