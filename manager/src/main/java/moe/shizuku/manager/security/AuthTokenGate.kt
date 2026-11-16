package moe.shizuku.manager.security

enum class AuthTokenStatus {
    VALID,
    MISSING,
    INVALID,
}

/**
 * Pure token gate and notification throttle for the exported automation receivers.
 *
 * The comparison is intentionally length-safe instead of using `String.equals`, and the
 * notification decision is separate so a spamming sender cannot turn a wrong token into an
 * unbounded stream of high-priority notifications.
 */
object AuthTokenGate {

    const val NOTIFY_WINDOW_MS = 5 * 60 * 1000L

    fun evaluate(expectedToken: String?, providedToken: String?): AuthTokenStatus {
        if (providedToken.isNullOrEmpty()) return AuthTokenStatus.MISSING
        if (expectedToken.isNullOrEmpty()) return AuthTokenStatus.INVALID
        return if (constantTimeEquals(expectedToken, providedToken)) {
            AuthTokenStatus.VALID
        } else {
            AuthTokenStatus.INVALID
        }
    }

    fun shouldNotify(
        nowElapsedRealtime: Long,
        lastNotifiedElapsedRealtime: Long,
        windowMillis: Long = NOTIFY_WINDOW_MS,
    ): Boolean {
        if (lastNotifiedElapsedRealtime <= 0L) return true
        // A reboot resets elapsedRealtime; do not let a stale timestamp suppress the first warning.
        if (nowElapsedRealtime < lastNotifiedElapsedRealtime) return true
        return nowElapsedRealtime - lastNotifiedElapsedRealtime >= windowMillis
    }

    internal fun constantTimeEquals(expected: String, provided: String): Boolean {
        val expectedBytes = expected.toByteArray(Charsets.UTF_8)
        val providedBytes = provided.toByteArray(Charsets.UTF_8)

        var difference = expectedBytes.size xor providedBytes.size
        val length = maxOf(expectedBytes.size, providedBytes.size)
        for (i in 0 until length) {
            val expectedByte = if (i < expectedBytes.size) expectedBytes[i].toInt() else 0
            val providedByte = if (i < providedBytes.size) providedBytes[i].toInt() else 0
            difference = difference or (expectedByte xor providedByte)
        }
        return difference == 0
    }
}
