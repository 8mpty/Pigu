package moe.shizuku.manager.management

/**
 * Pure filter decisions for App Management. Kept separate from the activity so the "Denied"
 * semantics are testable: it means the last recorded permission decision, not a separate
 * persisted permission model.
 */
object AppFilterPolicy {

    enum class Filter {
        ALL,
        ALLOWED,
        DENIED,
        COMPANION,
    }

    enum class Sort {
        NAME,
        RECENT,
    }

    fun matches(filter: Filter, granted: Boolean, denied: Boolean, needsCompanion: Boolean): Boolean =
        when (filter) {
            Filter.ALL -> true
            Filter.ALLOWED -> granted
            Filter.DENIED -> denied
            Filter.COMPANION -> needsCompanion
        }
}
