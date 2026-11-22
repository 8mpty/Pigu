package moe.shizuku.manager.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTokenGateTest {

    @Test
    fun validTokenIsAccepted() {
        assertEquals(AuthTokenStatus.VALID, AuthTokenGate.evaluate("AbC123", "AbC123"))
    }

    @Test
    fun missingTokenIsReportedSeparately() {
        assertEquals(AuthTokenStatus.MISSING, AuthTokenGate.evaluate("AbC123", null))
        assertEquals(AuthTokenStatus.MISSING, AuthTokenGate.evaluate("AbC123", ""))
    }

    @Test
    fun wrongTokenIsInvalid() {
        assertEquals(AuthTokenStatus.INVALID, AuthTokenGate.evaluate("AbC123", "AbC124"))
        assertEquals(AuthTokenStatus.INVALID, AuthTokenGate.evaluate("AbC123", "abc123"))
        assertEquals(AuthTokenStatus.INVALID, AuthTokenGate.evaluate("AbC123", "AbC1234"))
        assertEquals(AuthTokenStatus.INVALID, AuthTokenGate.evaluate(null, "AbC123"))
        assertEquals(AuthTokenStatus.INVALID, AuthTokenGate.evaluate("", "AbC123"))
    }

    @Test
    fun comparisonHandlesDifferentLengthsWithoutThrowing() {
        assertFalse(AuthTokenGate.constantTimeEquals("short", "a much longer token"))
        assertTrue(AuthTokenGate.constantTimeEquals("same", "same"))
    }

    @Test
    fun firstNotificationIsAlwaysAllowed() {
        assertTrue(AuthTokenGate.shouldNotify(nowElapsedRealtime = 1_000, lastNotifiedElapsedRealtime = -1))
        assertTrue(AuthTokenGate.shouldNotify(nowElapsedRealtime = 1_000, lastNotifiedElapsedRealtime = 0))
    }

    @Test
    fun notificationIsSuppressedInsideTheWindow() {
        assertFalse(AuthTokenGate.shouldNotify(1_000, 1_000))
        assertFalse(AuthTokenGate.shouldNotify(1_000 + AuthTokenGate.NOTIFY_WINDOW_MS - 1, 1_000))
    }

    @Test
    fun notificationIsAllowedAtTheWindowBoundary() {
        assertTrue(AuthTokenGate.shouldNotify(1_000 + AuthTokenGate.NOTIFY_WINDOW_MS, 1_000))
    }

    @Test
    fun rebootResetsTheClockAndAllowsANotification() {
        assertTrue(AuthTokenGate.shouldNotify(nowElapsedRealtime = 500, lastNotifiedElapsedRealtime = 50_000))
    }
}
