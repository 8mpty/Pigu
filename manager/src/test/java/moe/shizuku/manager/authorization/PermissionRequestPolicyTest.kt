package moe.shizuku.manager.authorization

import moe.shizuku.manager.authorization.PermissionRequestPolicy.Rejection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequestPolicyTest {

    private fun evaluate(
        uid: Int = 10_123,
        pid: Int = 4242,
        packageName: String? = "com.example.app",
        applicationUid: Int = 10_123,
        packagesForUid: List<String>? = listOf("com.example.app"),
        label: CharSequence? = "Example App",
        fallback: CharSequence? = "com.example.app",
    ): PermissionRequestPolicy.Decision = PermissionRequestPolicy.evaluate(
        uid,
        pid,
        packageName,
        applicationUid,
        packagesForUid,
        label,
        fallback,
    )

    @Test
    fun validRequestIsAccepted() {
        val decision = evaluate()
        assertTrue(decision.accepted)
        assertEquals("com.example.app", decision.packageName)
        assertEquals("Example App", decision.label)
        assertTrue(decision.sharedUidPackages.isEmpty())
    }

    @Test
    fun uidAndPidMustBePositive() {
        assertEquals(Rejection.INVALID_UID, evaluate(uid = 0).rejection)
        assertEquals(Rejection.INVALID_UID, evaluate(uid = -1).rejection)
        assertEquals(Rejection.INVALID_PID, evaluate(pid = 0).rejection)
        assertEquals(Rejection.INVALID_PID, evaluate(pid = -1).rejection)
    }

    @Test
    fun applicationInfoMustBelongToTheRequestingUid() {
        val decision = evaluate(applicationUid = 10_999)
        assertFalse(decision.accepted)
        assertEquals(Rejection.APPLICATION_UID_MISMATCH, decision.rejection)
    }

    @Test
    fun packageListIsAdvisoryBecausePackageVisibilityCanHideTheCaller() {
        // Android filters getPackagesForUid() through package visibility. A valid caller can
        // therefore be absent from the list; the ApplicationInfo uid check is the hard gate.
        val decision = evaluate(packagesForUid = listOf("com.example.other"))
        assertTrue(decision.accepted)
        assertEquals(listOf("com.example.other"), decision.sharedUidPackages)
    }

    @Test
    fun emptyPackageListIsAccepted() {
        assertTrue(evaluate(packagesForUid = emptyList()).accepted)
    }

    @Test
    fun unknownPackageListIsAccepted() {
        // Some devices do not expose packages for a uid. The ApplicationInfo uid check still
        // anchors the request in that case.
        assertTrue(evaluate(packagesForUid = null).accepted)
    }

    @Test
    fun invalidPackageNameIsRejected() {
        assertEquals(Rejection.INVALID_PACKAGE_NAME, evaluate(packageName = null).rejection)
        assertEquals(Rejection.INVALID_PACKAGE_NAME, evaluate(packageName = "").rejection)
        assertEquals(Rejection.INVALID_PACKAGE_NAME, evaluate(packageName = "com.example app").rejection)
        assertEquals(Rejection.INVALID_PACKAGE_NAME, evaluate(packageName = "com.example/app").rejection)
    }

    @Test
    fun sharedUidPackagesAreDisclosed() {
        val decision = evaluate(
            packagesForUid = listOf(
                "com.example.shared",
                "com.example.app",
                "com.example.shared",
                "com.example.other",
            ),
        )
        assertTrue(decision.accepted)
        assertEquals(
            listOf("com.example.other", "com.example.shared"),
            decision.sharedUidPackages,
        )
    }

    @Test
    fun labelStopsAtFirstLineAndRemovesDirectionControls() {
        assertEquals("Good app", PermissionRequestPolicy.sanitizeLabel("Good app\nis Pigu"))
        assertEquals("Good app", PermissionRequestPolicy.sanitizeLabel("\u202EGood app\u202C"))
        assertEquals("Good app", PermissionRequestPolicy.sanitizeLabel("Good\u0000 app"))
        assertEquals("Good app", PermissionRequestPolicy.sanitizeLabel("  Good   app  "))
    }

    @Test
    fun blankLabelFallsBackToPackageName() {
        assertEquals(
            "com.example.app",
            PermissionRequestPolicy.sanitizeLabel("\u202E", "com.example.app"),
        )
        assertEquals(
            "com.example.app",
            PermissionRequestPolicy.evaluate(
                10_123,
                4242,
                "com.example.app",
                10_123,
                listOf("com.example.app"),
                "\n",
                "com.example.app",
            ).label,
        )
    }

    @Test
    fun labelIsBoundedWithoutSplittingASurrogatePair() {
        val ascii = PermissionRequestPolicy.sanitizeLabel("a".repeat(600))
        assertEquals(PermissionRequestPolicy.MAX_LABEL_LENGTH, ascii.length)

        val emoji = PermissionRequestPolicy.sanitizeLabel("a".repeat(499) + "\uD83D\uDE00")
        assertEquals(499, emoji.length)
        assertFalse(Character.isHighSurrogate(emoji.last()))
    }

    @Test
    fun packageNameSanitizerStripsUnsupportedCharacters() {
        assertEquals("com.example_app", PermissionRequestPolicy.sanitizePackageName("com.example_app"))
        assertEquals("com.exampleapp", PermissionRequestPolicy.sanitizePackageName("com.example app"))
    }
}
