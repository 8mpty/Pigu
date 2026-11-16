package moe.shizuku.manager.adb

/**
 * The wait schedule used when adbd restarts into TCP mode.
 *
 * The old code retried five times with one-second steps and treated the first socket timeout as
 * fatal. adbd can take longer than that to tear down USB and start listening again, so the
 * schedule starts short, backs off, and keeps trying long enough for a slow device without
 * holding the UI forever.
 */
class PortRetrySchedule(private val delaysMs: LongArray) {

    init {
        require(delaysMs.isNotEmpty()) { "at least one retry is required" }
        require(delaysMs.all { it >= 0 }) { "delays must not be negative" }
    }

    val attempts: Int
        get() = delaysMs.size

    fun delayBefore(attempt: Int): Long {
        require(attempt in delaysMs.indices) { "attempt $attempt is outside the schedule" }
        return delaysMs[attempt]
    }

    companion object {
        val DEFAULT = PortRetrySchedule(
            longArrayOf(0, 500, 1_000, 2_000, 4_000, 8_000, 12_000)
        )
    }
}
