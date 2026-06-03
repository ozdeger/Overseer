package games.ace.overseer.review

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import games.ace.overseer.settings.OverseerSettings
import java.util.concurrent.atomic.AtomicInteger

/**
 * Entry point for reviews. Commits are reviewed in parallel on the [ReviewQueue] pool.
 * Safe to call from any thread.
 */
object ReviewLauncher {

    /** Most recent commits to review in one go (used for catch-up after the IDE was closed). */
    private const val MAX_COMMITS = 5
    private val LOG = logger<ReviewLauncher>()

    fun onCommits(project: Project, repoRoot: String, branch: String, oldShaHint: String, newSha: String) {
        val settings = OverseerSettings.getInstance()
        if (!settings.autoReviewOnCommit) return
        if (project.isDisposed) return

        val queue = ReviewQueue.getInstance(project)
        queue.submit { indicator ->
            val store = ReviewStore.getInstance(project)
            val key = "$repoRoot@$branch"
            val from = store.getBranchTip(key) ?: oldShaHint
            if (from == newSha) return@submit

            val myEmail = if (settings.reviewOnlyMyCommits) {
                GitCli.userEmail(repoRoot, indicator).ifBlank { null }
            } else {
                null
            }

            val metas = GitCli.commitsForPush(repoRoot, from, newSha, indicator)
            val mine = if (myEmail == null) metas
            else metas.filter { it.committerEmail.equals(myEmail, ignoreCase = true) }
            val fresh = mine.filter { store.find(repoRoot, it.hash) == null }
            val targets = fresh.take(MAX_COMMITS).map {
                ReviewWorker.Target(repoRoot, it.hash, it.subject, it.timeMillis)
            }

            LOG.info("Overseer onCommits $branch ${from.take(8)}..${newSha.take(8)}: myEmail=$myEmail raw=${metas.size} mine=${mine.size} fresh=${fresh.size}")
            if (myEmail != null && metas.isNotEmpty() && mine.isEmpty()) {
                LOG.info("Overseer: nothing matched committer '$myEmail'; committers seen=${metas.map { it.committerEmail }.distinct()}")
            }

            if (targets.isEmpty()) {
                if (!indicator.isCanceled) store.setBranchTip(key, newSha)
                return@submit
            }

            targets.forEach {
                store.markInProgress(CommitReview(it.repoRoot, it.commitHash, it.subject, Verdict.OK, "", it.timeMillis))
            }

            val remaining = AtomicInteger(targets.size)
            targets.forEach { t ->
                queue.submit { ind ->
                    try {
                        ReviewWorker.reviewOne(project, t, ind)
                    } finally {
                        if (remaining.decrementAndGet() == 0 && !ind.isCanceled) {
                            store.setBranchTip(key, newSha)
                        }
                    }
                }
            }
        }
    }

    /** Explicitly (re-)review a single commit from scratch — used by the "Review Again" action. */
    fun reviewCommit(project: Project, repoRoot: String, commitHash: String, subject: String, timeMillis: Long) {
        if (project.isDisposed) return
        val store = ReviewStore.getInstance(project)
        store.markInProgress(CommitReview(repoRoot, commitHash, subject, Verdict.OK, "", timeMillis))
        ReviewQueue.getInstance(project).submit { ind ->
            ReviewWorker.reviewOne(project, ReviewWorker.Target(repoRoot, commitHash, subject, timeMillis), ind)
        }
    }

    /**
     * Re-evaluate an existing review given the author's [note]. Sends the same commit diff plus the
     * previous review and the note as context, then appends the exchange to the review as a thread.
     */
    fun reEvaluate(project: Project, review: CommitReview, note: String) {
        if (project.isDisposed) return
        val store = ReviewStore.getInstance(project)

        // Keep the existing thread visible while re-evaluating.
        store.markInProgress(
            CommitReview(review.repoPath, review.commitHash, review.subject, review.verdict, review.body, review.timestamp).apply {
                inputTokens = review.inputTokens
                outputTokens = review.outputTokens
                costUsd = review.costUsd
            }
        )

        val repoRoot = review.repoPath
        val hash = review.commitHash
        val subject = review.subject
        val time = review.timestamp
        val prevBody = review.body
        val prevIn = review.inputTokens
        val prevOut = review.outputTokens
        val prevCost = review.costUsd

        ReviewQueue.getInstance(project).submit { ind ->
            val extensions = OverseerSettings.getInstance().allowedExtensionList()
            val diff = GitCli.commitDiff(repoRoot, hash, ind, extensions)
            val context = buildString {
                append("The author has responded to your previous review of this commit. ")
                append("Re-evaluate taking their response into account and produce an updated review.\n\n")
                append("--- PREVIOUS REVIEW ---\n").append(prevBody).append("\n\n")
                append("--- AUTHOR'S RESPONSE ---\n").append(note)
            }
            val res = ClaudeReviewService.review(ReviewRequest(repoRoot, hash.take(8), diff), ind, context)
            val newBody = buildString {
                append(prevBody.trimEnd())
                append("\n\n---\n\n")
                append("**Your response:** ").append(note).append("\n\n")
                append("**Re-evaluation:**\n\n").append(res.output)
            }
            val updated = CommitReview(repoRoot, hash, subject, res.verdict, newBody, time).apply {
                inputTokens = prevIn + res.inputTokens
                outputTokens = prevOut + res.outputTokens
                costUsd = prevCost + res.costUsd
            }
            store.complete(updated)
            if (updated.verdict == Verdict.ERROR) OverseerNotifier.notifyReviewError(project, updated)
        }
    }
}
