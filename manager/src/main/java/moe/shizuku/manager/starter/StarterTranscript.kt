package moe.shizuku.manager.starter

import android.content.Context
import java.io.File

/**
 * Keeps the last Starter screen transcript after the activity is closed.
 *
 * The file lives in the app's private files directory, which is not included by the backup
 * descriptors. It is capped so a pathological log cannot grow without bound. The Service Doctor
 * reads it and shows it when the last start failed.
 */
object StarterTranscript {

    private const val PREFS = "starter_last"
    private const val KEY_TIME = "time"
    private const val KEY_SUCCESS = "success"
    private const val KEY_MESSAGE = "message"
    private const val FILE_NAME = "starter-last.txt"
    private const val MAX_BYTES = 64 * 1024

    data class LastStart(
        val timestamp: Long,
        val success: Boolean,
        val message: String?,
    )

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun begin(context: Context) {
        runCatching {
            file(context).writeText("")
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TIME, System.currentTimeMillis())
            .putBoolean(KEY_SUCCESS, false)
            .putString(KEY_MESSAGE, "Start did not finish")
            .apply()
    }

    @Synchronized
    fun append(context: Context, line: String) {
        runCatching {
            val f = file(context)
            if (f.length() > MAX_BYTES) {
                // Keep the tail: the beginning of a start is usually less useful than the end.
                val tail = f.readText().takeLast(MAX_BYTES / 2)
                f.writeText(tail)
            }
            f.appendText(line)
            if (!line.endsWith("\n")) f.appendText("\n")
        }
    }

    fun finish(context: Context, success: Boolean, message: String?) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TIME, System.currentTimeMillis())
            .putBoolean(KEY_SUCCESS, success)
            .putString(KEY_MESSAGE, message?.take(500))
            .apply()
    }

    fun read(context: Context): String =
        runCatching { file(context).readText() }.getOrElse { "" }

    fun last(context: Context): LastStart? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val time = prefs.getLong(KEY_TIME, 0L)
        if (time <= 0L) return null
        return LastStart(
            timestamp = time,
            success = prefs.getBoolean(KEY_SUCCESS, false),
            message = prefs.getString(KEY_MESSAGE, null),
        )
    }
}
