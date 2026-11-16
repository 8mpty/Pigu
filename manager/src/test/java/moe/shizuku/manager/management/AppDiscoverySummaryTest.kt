package moe.shizuku.manager.management

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDiscoverySummaryTest {

    @Test
    fun companionCandidatesAreOnlyLegacyAppsWhenCompanionIsMissing() {
        val summary = AppDiscoverySummary(
            total = 10,
            authorized = 4,
            legacyClients = 3,
            companionInstalled = false,
        )
        assertEquals(3, summary.companionCandidates)
    }

    @Test
    fun installedCompanionRemovesTheCandidateCount() {
        val summary = AppDiscoverySummary(
            total = 10,
            authorized = 4,
            legacyClients = 3,
            companionInstalled = true,
        )
        assertEquals(0, summary.companionCandidates)
    }

    @Test
    fun modernClientsDoNotBecomeCandidates() {
        val summary = AppDiscoverySummary(
            total = 2,
            authorized = 2,
            legacyClients = 0,
            companionInstalled = false,
        )
        assertEquals(0, summary.companionCandidates)
    }
}
