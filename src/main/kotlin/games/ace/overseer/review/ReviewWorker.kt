package games.ace.overseer.review

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import games.ace.overseer.settings.OverseerSettings

/** Reviews a single commit and writes the result into [ReviewStore]. */
object ReviewWorker {

    data class Target(val repoRoot: String, val commitHash: String, val subject: String, val timeMillis: Long)

    fun reviewOne(project: Project, t: Target, indicator: ProgressIndicator) {
        val store = ReviewStore.getInstance(project)
        val key = "${t.repoRoot}@${t.commitHash}"
        if (indicator.isCanceled) { store.dropInProgress(key); return }

        val extensions = OverseerSettings.getInstance().allowedExtensionList()
        indicator.text = "Overseer: reviewing ${t.commitHash.take(8)} - ${t.subject}"

        val diff = GitCli.commitDiff(t.repoRoot, t.commitHash, indicator, extensions)
        if (diff.isBlank()) {
            LOG.info("Overseer: ${t.commitHash.take(8)} has no reviewable files (extension allowlist) - skipped")
            store.dropInProgress(key)
            return
        }

        val review = try {
            val res = ClaudeReviewService.review(ReviewRequest(t.repoRoot, t.commitHash.take(8), diff), indicator)
            CommitReview(t.repoRoot, t.commitHash, t.subject, res.verdict, res.output, t.timeMillis).apply {
                inputTokens = res.inputTokens
                outputTokens = res.outputTokens
                costUsd = res.costUsd
            }
        } catch (e: Exception) {
            LOG.warn("Overseer review failed for ${t.commitHash} in ${t.repoRoot}", e)
            CommitReview(t.repoRoot, t.commitHash, t.subject, Verdict.ERROR, "Review failed: ${e.message}", t.timeMillis)
        }
        store.complete(review)
        if (review.verdict == Verdict.ERROR) OverseerNotifier.notifyReviewError(project, review)
    }

    private val LOG = logger<ReviewWorker>()
}
