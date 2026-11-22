package moe.shizuku.manager.receiver

/**
 * Pure boot-claim decision.
 *
 * `BOOT_COMPLETED` can be delivered more than once for the same boot (for example when a
 * receiver is enabled after boot). Only the first delivery should start the service.
 */
object BootClaim {

    fun shouldStart(bootCount: Int, lastClaimedBootCount: Int): Boolean {
        // BOOT_COUNT is unavailable on some builds. Falling back to starting is safer than
        // silently disabling start-on-boot.
        if (bootCount < 0) return true
        return lastClaimedBootCount != bootCount
    }
}
