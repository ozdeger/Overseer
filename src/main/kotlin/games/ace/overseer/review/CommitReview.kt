package games.ace.overseer.review

/**
 * Severity of a single commit's review (advisory only, never blocks).
 * Ordered least to most severe.
 */
enum class Verdict(val emoji: String, val label: String) {
    OK("✅", "OK"),
    INFO("ℹ️", "INFO"),
    WARNING("⚠️", "WARNING"),
    ERROR("🔴", "ERROR");
}

/**
 * One stored review, keyed by repo + commit. Plain class with defaults so the IntelliJ
 * XML serializer can persist it inside [ReviewStore].
 */
class CommitReview() {
    var repoPath: String = ""
    var commitHash: String = ""
    var subject: String = ""
    var verdict: Verdict = Verdict.OK
    var body: String = ""
    var timestamp: Long = 0L

    /** Transient: true while the review is queued/running. Never persisted (see ReviewStore). */
    var inProgress: Boolean = false

    /** Token usage / cost reported by the claude CLI (0 when unknown). */
    var inputTokens: Int = 0
    var outputTokens: Int = 0
    var costUsd: Double = 0.0

    constructor(
        repoPath: String,
        commitHash: String,
        subject: String,
        verdict: Verdict,
        body: String,
        timestamp: Long,
    ) : this() {
        this.repoPath = repoPath
        this.commitHash = commitHash
        this.subject = subject
        this.verdict = verdict
        this.body = body
        this.timestamp = timestamp
    }

    val shortHash: String get() = commitHash.take(8)

    /** Stable identity for de-duplication. */
    val key: String get() = "$repoPath@$commitHash"
}
