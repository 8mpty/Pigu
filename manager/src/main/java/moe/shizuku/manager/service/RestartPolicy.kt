package moe.shizuku.manager.service

import java.util.ArrayDeque

/**
 * Bounds the watchdog's own restart attempts.
 *
 * A crash is worth a restart. A start that keeps failing is not: retrying it forever is what
 * turns one broken setup into a battery drain and a stream of notifications. The policy allows a
 * small number of attempts inside a window, spaces them apart, and then stops until a server is
 * confirmed running or the watchdog is turned off and on again.
 */
class RestartPolicy(
    private val maxAttempts: Int,
    private val windowMs: Long,
    private val cooldownMs: Long,
) {

    enum class Decision {
        ALLOW,
        COOLDOWN,
        GIVE_UP,
    }

    private val attempts = ArrayDeque<Long>()
    private var exhausted = false

    val isExhausted: Boolean
        get() = exhausted

    fun decide(nowElapsedRealtime: Long): Decision {
        if (exhausted) return Decision.GIVE_UP

        prune(nowElapsedRealtime)
        if (attempts.size >= maxAttempts) {
            exhausted = true
            return Decision.GIVE_UP
        }

        val last = attempts.peekLast()
        if (last != null && nowElapsedRealtime - last < cooldownMs) {
            return Decision.COOLDOWN
        }
        return Decision.ALLOW
    }

    fun recordAttempt(nowElapsedRealtime: Long) {
        prune(nowElapsedRealtime)
        attempts.addLast(nowElapsedRealtime)
        if (attempts.size >= maxAttempts) exhausted = true
    }

    fun reset() {
        attempts.clear()
        exhausted = false
    }

    private fun prune(nowElapsedRealtime: Long) {
        while (attempts.isNotEmpty()) {
            val oldest = attempts.peekFirst() ?: break
            if (nowElapsedRealtime - oldest < windowMs) break
            attempts.removeFirst()
        }
    }
}
