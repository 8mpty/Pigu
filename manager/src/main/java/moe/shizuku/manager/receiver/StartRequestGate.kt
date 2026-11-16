package moe.shizuku.manager.receiver

/**
 * Suppresses duplicate start requests while one is already being handled.
 *
 * The gate is deliberately time-bounded: a start that never reports back must not block every
 * later request forever. It is released immediately when the service reaches a terminal state.
 */
class StartRequestGate(private val maxInFlightMs: Long) {

    private var inFlightSince = 0L

    fun tryAcquire(nowElapsedRealtime: Long): Boolean {
        if (isInFlight(nowElapsedRealtime)) return false
        inFlightSince = nowElapsedRealtime
        return true
    }

    fun release() {
        inFlightSince = 0L
    }

    fun isInFlight(nowElapsedRealtime: Long): Boolean {
        if (inFlightSince <= 0L) return false
        if (nowElapsedRealtime < inFlightSince) return false
        return nowElapsedRealtime - inFlightSince < maxInFlightMs
    }
}
