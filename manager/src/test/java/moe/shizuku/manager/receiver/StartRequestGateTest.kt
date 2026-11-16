package moe.shizuku.manager.receiver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartRequestGateTest {

    private fun gate() = StartRequestGate(maxInFlightMs = 3 * 60_000L)

    @Test
    fun oneRequestCanBeInFlight() {
        val gate = gate()
        assertTrue(gate.tryAcquire(1_000))
        assertTrue(gate.isInFlight(1_001))
        assertFalse(gate.tryAcquire(1_001))
    }

    @Test
    fun releaseAllowsTheNextRequest() {
        val gate = gate()
        assertTrue(gate.tryAcquire(1_000))
        gate.release()

        assertFalse(gate.isInFlight(1_001))
        assertTrue(gate.tryAcquire(1_001))
    }

    @Test
    fun abandonedRequestExpires() {
        val gate = gate()
        assertTrue(gate.tryAcquire(1_000))

        assertFalse(gate.isInFlight(3 * 60_000L + 1_000))
        assertTrue(gate.tryAcquire(3 * 60_000L + 1_000))
    }

    @Test
    fun clockGoingBackwardsDoesNotBlockForever() {
        val gate = gate()
        assertTrue(gate.tryAcquire(50_000))

        assertFalse(gate.isInFlight(1_000))
        assertTrue(gate.tryAcquire(1_000))
    }
}
