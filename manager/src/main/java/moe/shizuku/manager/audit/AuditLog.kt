package moe.shizuku.manager.audit

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small, local-only ring buffer of access decisions.
 *
 * It deliberately lives in its own preferences file (`audit_log.xml`), which is not listed in
 * either backup descriptor, so the log never leaves the device through cloud backup or device
 * transfer. The log is capped, contains no auth token, and can be cleared at any time from the
 * Developer page.
 *
 * This is an accountability aid, not a security boundary: a user with root can edit app data.
 */
object AuditLog {

    private const val PREFS_NAME = "audit_log"
    private const val KEY_ENTRIES = "entries"
    private const val LIMIT = 400

    enum class Action(val key: String) {
        PERMISSION("permission"),
        GRANT("grant"),
        REVOKE("revoke"),
        PAUSE("pause"),
        RESUME("resume"),
        AUTOMATION_START("automation_start"),
        AUTOMATION_STOP("automation_stop"),
        COMPANION("companion"),
    }

    enum class Result(val key: String) {
        SHOWN("shown"),
        ALLOWED("allowed"),
        DENIED("denied"),
        OK("ok"),
        FAILED("failed"),
        CONFLICT("conflict"),
    }

    data class Entry(
        val timestamp: Long,
        val action: Action,
        val result: Result,
        val packageName: String?,
        val uid: Int?,
        val detail: String?,
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun record(
        context: Context,
        action: Action,
        result: Result,
        packageName: String? = null,
        uid: Int? = null,
        detail: String? = null,
    ) {
        val entries = readArray(context)
        entries.put(
            JSONObject().apply {
                put("t", System.currentTimeMillis())
                put("a", action.key)
                put("r", result.key)
                packageName?.let { put("p", it) }
                uid?.let { put("u", it) }
                detail?.let { put("d", it.take(200)) }
            }
        )

        // Ring buffer: drop the oldest entries first.
        while (entries.length() > LIMIT) {
            entries.remove(0)
        }
        prefs(context).edit().putString(KEY_ENTRIES, entries.toString()).apply()
    }

    @Synchronized
    fun entries(context: Context): List<Entry> {
        val array = readArray(context)
        val result = ArrayList<Entry>(array.length())
        // Newest first for display.
        for (i in array.length() - 1 downTo 0) {
            val entry = parse(array.optJSONObject(i)) ?: continue
            result.add(entry)
        }
        return result
    }

    @Synchronized
    fun count(context: Context): Int = readArray(context).length()

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ENTRIES).apply()
    }

    /** The last permission decision for a package, used by the App Management "Denied" filter. */
    @Synchronized
    fun lastDecisions(context: Context): Map<String, Boolean> {
        val array = readArray(context)
        val decisions = HashMap<String, Boolean>()
        for (i in array.length() - 1 downTo 0) {
            val entry = parse(array.optJSONObject(i)) ?: continue
            if (entry.action != Action.PERMISSION) continue
            if (entry.result != Result.ALLOWED && entry.result != Result.DENIED) continue
            val key = keyOf(entry.packageName, entry.uid)
            if (!decisions.containsKey(key)) {
                decisions[key] = entry.result == Result.ALLOWED
            }
        }
        return decisions
    }

    fun decisionFor(
        decisions: Map<String, Boolean>,
        packageName: String?,
        uid: Int?,
    ): Boolean? = decisions[keyOf(packageName, uid)]

    @Synchronized
    fun lastFor(context: Context, packageName: String?, uid: Int?): Entry? {
        val array = readArray(context)
        for (i in array.length() - 1 downTo 0) {
            val entry = parse(array.optJSONObject(i)) ?: continue
            if (matches(entry, packageName, uid)) return entry
        }
        return null
    }

    fun lastFor(entries: List<Entry>, packageName: String?, uid: Int?): Entry? =
        entries.firstOrNull { matches(it, packageName, uid) }

    private fun matches(entry: Entry, packageName: String?, uid: Int?): Boolean {
        if (packageName != null && uid != null) {
            return entry.packageName == packageName && entry.uid == uid
        }
        if (packageName != null) {
            return entry.packageName == packageName
        }
        if (uid != null) {
            return entry.uid == uid
        }
        return false
    }

    private fun keyOf(packageName: String?, uid: Int?): String = when {
        packageName != null && uid != null -> "$packageName#${uid}"
        packageName != null -> packageName
        else -> "uid:${uid ?: -1}"
    }

    fun format(context: Context, entries: List<Entry>): String {
        if (entries.isEmpty()) return context.getString(
            moe.shizuku.manager.R.string.audit_log_empty
        )

        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return buildString {
            for (entry in entries) {
                append(formatter.format(Date(entry.timestamp)))
                append("  ")
                append(actionLabel(entry.action))
                append("  ")
                append(resultLabel(entry.result))
                entry.packageName?.let {
                    append("  ")
                    append(it)
                    entry.uid?.let { uid -> append(" (").append(uid).append(')') }
                }
                entry.detail?.let {
                    append("  [").append(it).append(']')
                }
                append('\n')
            }
        }.trimEnd()
    }

    fun actionLabel(action: Action): String = when (action) {
        Action.PERMISSION -> "permission"
        Action.GRANT -> "grant"
        Action.REVOKE -> "revoke"
        Action.PAUSE -> "pause"
        Action.RESUME -> "resume"
        Action.AUTOMATION_START -> "automation start"
        Action.AUTOMATION_STOP -> "automation stop"
        Action.COMPANION -> "companion"
    }

    fun resultLabel(result: Result): String = when (result) {
        Result.SHOWN -> "shown"
        Result.ALLOWED -> "allowed"
        Result.DENIED -> "denied"
        Result.OK -> "ok"
        Result.FAILED -> "failed"
        Result.CONFLICT -> "conflict"
    }

    private fun readArray(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (e: Throwable) {
            // A corrupt entry must not hide the whole page; start clean instead.
            prefs(context).edit().remove(KEY_ENTRIES).apply()
            JSONArray()
        }
    }

    private fun parse(json: JSONObject?): Entry? {
        if (json == null) return null
        val action = Action.entries.firstOrNull { it.key == json.optString("a") } ?: return null
        val result = Result.entries.firstOrNull { it.key == json.optString("r") } ?: return null
        return Entry(
            timestamp = json.optLong("t", 0L),
            action = action,
            result = result,
            packageName = json.optString("p").takeIf { it.isNotEmpty() },
            uid = if (json.has("u")) json.optInt("u") else null,
            detail = json.optString("d").takeIf { it.isNotEmpty() },
        )
    }
}
