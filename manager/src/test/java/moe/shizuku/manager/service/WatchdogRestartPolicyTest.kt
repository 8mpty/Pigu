package moe.shizuku.manager.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchdogRestartPolicyTest {

    private fun policy() = RestartPolicy(
        maxAttempts = 3,
        windowMs = 15 * 60_000L,
        cooldownMs = 90_000L,
    )

    @Test
    fun firstAttemptIsAllowed() {
        assertEquals(RestartPolicy.Decision.ALLOW, policy().decide(1_000))
    }

    @Test
    fun secondAttemptInsideCooldownIsDeferred() {
        val policy = policy()
        assertEquals(RestartPolicy.Decision.ALLOW, policy.decide(1_000))
        policy.recordAttempt(1_000)

        assertEquals(RestartPolicy.Decision.COOLDOWN, policy.decide(1_001))
        assertEquals(RestartPolicy.Decision.COOLDOWN, policy.decide(90_999))
    }

    @Test
    fun attemptIsAllowedAgainAtTheCooldownBoundary() {
        val policy = policy()
        policy.recordAttempt(1_000)
        assertEquals(RestartPolicy.Decision.ALLOW, policy.decide(91_000))
    }

    @Test
    fun repeatedFailuresStopTheRestarts() {
        val policy = policy()
        policy.recordAttempt(1_000)
        policy.recordAttempt(100_000)
        policy.recordAttempt(200_000)

        assertTrue(policy.isExhausted)
        assertEquals(RestartPolicy.Decision.GIVE_UP, policy.decide(300_000))
        assertEquals(RestartPolicy.Decision.GIVE_UP, policy.decide(20 * 60_000L))
    }

    @Test
    fun runningServerResetsThePolicy() {
        val policy = policy()
        policy.recordAttempt(1_000)
        policy.recordAttempt(100_000)
        policy.recordAttempt(200_000)
        assertTrue(policy.isExhausted)

        policy.reset()

        assertFalse(policy.isExhausted)
        assertEquals(RestartPolicy.Decision.ALLOW, policy.decide(300_000))
    }

    @Test
    fun oldAttemptsFallOutsideTheWindow() {
        val policy = policy()
        policy.recordAttempt(1_000)
        policy.recordAttempt(100_000)

        // Both attempts are older than the 15-minute window, so this is a fresh start.
        assertEquals(RestartPolicy.Decision.ALLOW, policy.decide(20 * 60_000L))
    }
}
