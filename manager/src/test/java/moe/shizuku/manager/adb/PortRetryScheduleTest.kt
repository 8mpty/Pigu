package moe.shizuku.manager.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortRetryScheduleTest {

    @Test
    fun defaultScheduleWaitsLongEnoughForAdbdRestart() {
        val schedule = PortRetrySchedule.DEFAULT
        assertEquals(7, schedule.attempts)

        val total = (0 until schedule.attempts).sumOf { schedule.delayBefore(it) }
        assertTrue("total wait should cover a slow adbd restart", total >= 20_000)
    }

    @Test
    fun delaysStartImmediatelyAndOnlyGrow() {
        val schedule = PortRetrySchedule.DEFAULT
        assertEquals(0L, schedule.delayBefore(0))

        var previous = 0L
        for (attempt in 0 until schedule.attempts) {
            val delay = schedule.delayBefore(attempt)
            assertTrue(delay >= previous)
            previous = delay
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyScheduleIsRejected() {
        PortRetrySchedule(longArrayOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeDelayIsRejected() {
        PortRetrySchedule(longArrayOf(0, -1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeAttemptIsRejected() {
        PortRetrySchedule.DEFAULT.delayBefore(PortRetrySchedule.DEFAULT.attempts)
    }
}
