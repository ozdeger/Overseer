package plugins.ozdeger.observer.watch

/** One parsed reflog entry. */
data class ReflogEntry(val oldSha: String, val newSha: String, val message: String) {

    /**
     * True for tip changes that may introduce new commits worth reviewing (commit, merge,
     * cherry-pick, rebase, pull, amend, etc). Pure ref-moves that add no new content are excluded.
     * The actual set of new commits is still computed from the old..new range downstream, so a
     * no-op move (e.g. a backward reset that slips through) simply yields nothing to review.
     */
    val isReviewable: Boolean
        get() {
            val m = message.trimStart().lowercase()
            return !(m.startsWith("reset:") || m.startsWith("branch:") || m.startsWith("checkout:"))
        }
}

object ReflogParser {

    private val HEX = Regex("[0-9a-fA-F]{7,64}")

    fun parseLast(content: String): ReflogEntry? {
        val line = content.lineSequence().lastOrNull { it.isNotBlank() } ?: return null
        val tab = line.indexOf('\t')
        val meta = if (tab >= 0) line.substring(0, tab) else line
        val message = if (tab >= 0) line.substring(tab + 1) else ""
        val parts = meta.split(' ')
        if (parts.size < 2) return null
        val old = parts[0]
        val new = parts[1]
        if (!HEX.matches(old) || !HEX.matches(new)) return null
        return ReflogEntry(old, new, message)
    }
}
