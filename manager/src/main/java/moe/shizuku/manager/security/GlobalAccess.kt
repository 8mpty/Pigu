package moe.shizuku.manager.security

import android.os.Parcel
import android.util.Log
import moe.shizuku.manager.ShizukuSettings
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants
import rikka.shizuku.server.ServerConstants

/**
 * Manager-side half of the server's global access pause.
 *
 * The server is the enforcement point: when the flag is set it rejects every client except the
 * manager at the binder boundary. This object only sets or reads that flag. The local preference
 * is a cache for the case where the server is not running, so the UI can still show and resume a
 * pause without a binder.
 */
object GlobalAccess {

    private const val TAG = "GlobalAccess"

    fun isPaused(): Boolean {
        if (!Shizuku.pingBinder()) return ShizukuSettings.getAccessPaused()
        val binder = Shizuku.getBinder() ?: return ShizukuSettings.getAccessPaused()

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ShizukuApiConstants.BINDER_DESCRIPTOR)
            binder.transact(ServerConstants.BINDER_TRANSACTION_isAccessPaused, data, reply, 0)
            reply.readException()
            reply.readInt() != 0
        } catch (tr: Throwable) {
            Log.w(TAG, "Could not read the server pause flag; using the local preference", tr)
            ShizukuSettings.getAccessPaused()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * Sets the server flag. Returns false when the binder is missing or the server rejects the
     * transaction, for example because it is an older build without this control code.
     */
    fun setPaused(paused: Boolean): Boolean {
        val binder = Shizuku.getBinder() ?: return false

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ShizukuApiConstants.BINDER_DESCRIPTOR)
            data.writeInt(if (paused) 1 else 0)
            binder.transact(ServerConstants.BINDER_TRANSACTION_setAccessPaused, data, reply, 0)
            reply.readException()
            true
        } catch (tr: Throwable) {
            Log.w(TAG, "Could not set the server pause flag", tr)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * A new server can start with the pause still saved, or with it saved but the user having
     * already resumed. The saved manager preference is the user's intent, so apply it in both
     * directions whenever a new binder arrives. This also clears the server flag after a resume
     * that had to start a fresh process.
     */
    fun applyPersistedState() {
        if (!Shizuku.pingBinder()) return
        val paused = ShizukuSettings.getAccessPaused()
        if (!setPaused(paused)) {
            Log.w(TAG, "The running server could not apply the saved pause state")
        }
    }
}
