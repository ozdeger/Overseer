package games.ace.overseer.review

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil

/**
 * Bounded parallel review pool: up to [CONCURRENCY] reviews run at once. On project close the
 * shared indicator is cancelled (killing in-flight claude processes) and the executor shut down.
 */
@Service(Service.Level.PROJECT)
class ReviewQueue(private val project: Project) : Disposable {

    private val indicator = EmptyProgressIndicator()
    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Overseer-review", CONCURRENCY)

    /** Run a job on the pool; up to CONCURRENCY jobs execute in parallel. */
    fun submit(job: (ProgressIndicator) -> Unit) {
        executor.execute {
            if (project.isDisposed || indicator.isCanceled) return@execute
            try {
                job(indicator)
            } catch (e: Exception) {
                LOG.warn("Overseer review job failed", e)
                if (!project.isDisposed) OverseerNotifier.notifyError(project, e.message ?: e.toString())
            }
        }
    }

    override fun dispose() {
        indicator.cancel()
        executor.shutdownNow()
    }

    companion object {
        /** Max reviews running in parallel (matches the catch-up batch cap). */
        private const val CONCURRENCY = 5

        fun getInstance(project: Project): ReviewQueue = project.service()
        private val LOG = logger<ReviewQueue>()
    }
}
