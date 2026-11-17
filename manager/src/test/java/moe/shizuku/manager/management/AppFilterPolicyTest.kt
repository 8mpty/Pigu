package moe.shizuku.manager.management

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterPolicyTest {

    @Test
    fun allMatchesEveryApp() {
        assertTrue(AppFilterPolicy.matches(AppFilterPolicy.Filter.ALL, false, false, true))
        assertTrue(AppFilterPolicy.matches(AppFilterPolicy.Filter.ALL, true, true, false))
    }

    @Test
    fun allowedUsesGrantState() {
        assertTrue(AppFilterPolicy.matches(AppFilterPolicy.Filter.ALLOWED, true, false, false))
        assertFalse(AppFilterPolicy.matches(AppFilterPolicy.Filter.ALLOWED, false, true, true))
    }

    @Test
    fun deniedUsesLastDecision() {
        assertTrue(AppFilterPolicy.matches(AppFilterPolicy.Filter.DENIED, false, true, false))
        assertFalse(AppFilterPolicy.matches(AppFilterPolicy.Filter.DENIED, true, false, true))
    }

    @Test
    fun companionOnlyNeedsLegacyClientWithoutCompanion() {
        assertTrue(AppFilterPolicy.matches(AppFilterPolicy.Filter.COMPANION, false, false, true))
        assertFalse(AppFilterPolicy.matches(AppFilterPolicy.Filter.COMPANION, true, false, false))
    }
}
