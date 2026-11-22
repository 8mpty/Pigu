package moe.shizuku.manager.authorization

/**
 * Pure validation and presentation policy for permission requests.
 *
 * The Shizuku server tells the manager which app is asking, but the dialog is the last place
 * where a malformed or spoofed request can be rejected before the user is asked to trust it.
 * Keeping the decisions here makes them testable on the JVM.
 */
object PermissionRequestPolicy {

    const val MAX_LABEL_LENGTH = 500
    const val MAX_PACKAGE_NAME_LENGTH = 255
    private const val MAX_SHARED_PACKAGES = 20

    private const val LINE_BREAKS = "\n\r\u000B\u000C\u0085\u2028\u2029"

    enum class Rejection {
        INVALID_UID,
        INVALID_PID,
        INVALID_PACKAGE_NAME,
        APPLICATION_UID_MISMATCH,
    }

    data class Decision(
        val accepted: Boolean,
        val rejection: Rejection? = null,
        val packageName: String = "",
        val label: String = "",
        val sharedUidPackages: List<String> = emptyList(),
    )

    fun evaluate(
        uid: Int,
        pid: Int,
        packageName: String?,
        applicationUid: Int,
        packagesForUid: List<String>?,
        rawLabel: CharSequence?,
        fallbackLabel: CharSequence? = null,
    ): Decision {
        if (uid <= 0) return denied(Rejection.INVALID_UID)
        if (pid <= 0) return denied(Rejection.INVALID_PID)

        val safePackage = packageName?.let(::sanitizePackageName).orEmpty()
        if (safePackage.isEmpty() || safePackage != packageName) {
            return denied(Rejection.INVALID_PACKAGE_NAME)
        }

        if (applicationUid != uid) {
            return denied(Rejection.APPLICATION_UID_MISMATCH, safePackage)
        }

        // The package list is advisory. Android filters getPackagesForUid() through package
        // visibility, so a valid caller can be missing from it. Treat it as the source of the
        // shared-UID disclosure, not as proof that the request is valid.
        val sharedPackages = packagesForUid.orEmpty()
            .map(::sanitizePackageName)
            .filter { it.isNotEmpty() && it != safePackage }
            .distinct()
            .sorted()
            .take(MAX_SHARED_PACKAGES)

        return Decision(
            accepted = true,
            packageName = safePackage,
            label = sanitizeLabel(rawLabel, fallbackLabel ?: safePackage),
            sharedUidPackages = sharedPackages,
        )
    }

    /**
     * One line an app cannot use to look like another app: line breaks end the label, bidi and
     * other formatting controls are removed, whitespace is collapsed, and the result is bounded.
     */
    fun sanitizeLabel(label: CharSequence?, fallback: CharSequence? = null): String {
        val firstLine = firstSafeLine(label)
        if (firstLine.isNotEmpty()) return truncate(firstLine)
        return truncate(firstSafeLine(fallback))
    }

    fun sanitizePackageName(value: String): String {
        val builder = StringBuilder(minOf(value.length, MAX_PACKAGE_NAME_LENGTH))
        for (c in value) {
            if (builder.length >= MAX_PACKAGE_NAME_LENGTH) break
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '.' || c == '_') {
                builder.append(c)
            }
        }
        return builder.toString()
    }

    private fun denied(rejection: Rejection, packageName: String = ""): Decision =
        Decision(accepted = false, rejection = rejection, packageName = packageName)

    private fun firstSafeLine(value: CharSequence?): String {
        if (value == null) return ""

        val kept = StringBuilder()
        var pendingSpace = false
        for (c in value) {
            if (c in LINE_BREAKS) break

            val type = Character.getType(c)
            if (type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt()) continue

            if (c.isWhitespace()) {
                if (kept.isNotEmpty()) pendingSpace = true
                continue
            }

            if (pendingSpace) {
                kept.append(' ')
                pendingSpace = false
            }
            kept.append(c)
        }
        return kept.toString().trim()
    }

    private fun truncate(value: String): String {
        if (value.length <= MAX_LABEL_LENGTH) return value
        var end = MAX_LABEL_LENGTH
        if (Character.isHighSurrogate(value[end - 1])) end--
        return value.substring(0, end).trimEnd()
    }
}
