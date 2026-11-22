package moe.shizuku.manager.receiver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootClaimTest {

    @Test
    fun unknownBootCountAlwaysStarts() {
        assertTrue(BootClaim.shouldStart(-1, -1))
        assertTrue(BootClaim.shouldStart(-1, 12))
    }

    @Test
    fun firstClaimForABootStarts() {
        assertTrue(BootClaim.shouldStart(42, -1))
        assertTrue(BootClaim.shouldStart(42, 41))
    }

    @Test
    fun duplicateDeliveryForTheSameBootIsIgnored() {
        assertFalse(BootClaim.shouldStart(42, 42))
    }
}
