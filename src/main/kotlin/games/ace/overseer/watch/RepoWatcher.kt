package games.ace.overseer.watch

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import games.ace.overseer.review.ReviewLauncher
import games.ace.overseer.review.ReviewStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Watches one repository's branch reflogs (.git/logs/refs/heads) by polling on a short timer,
 * and triggers a review when a branch's tip advances.
 */
class RepoWatcher(private val project: Project, private val gitDir: Path) {

    private val repoRoot: String = (gitDir.parent ?: gitDir).toString()
    private val headsDir: Path = gitDir.resolve("logs").resolve("refs").resolve("heads")
    private val lastSeen = HashMap<Path, String>() // reflog file -> last new-sha handled
    @Volatile private var future: ScheduledFuture<*>? = null

    private data class RefState(val file: Path, val entry: ReflogEntry, val branch: String)

    fun start() {
        try {
            baseline()
        } catch (t: Throwable) {
            LOG.warn("Overseer baseline failed for $repoRoot", t)
        }
        future = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay({ safeScan() }, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS)
        LOG.info("Overseer polling $headsDir every ${POLL_SECONDS}s (headsDir exists=${Files.isDirectory(headsDir)})")
    }

    fun stop() {
        future?.cancel(false)
        future = null
    }

    private fun baseline() {
        val store = ReviewStore.getInstance(project)
        for (st in readReflogs()) {
            lastSeen[st.file] = st.entry.newSha
            val key = "$repoRoot@${st.branch}"
            val persisted = store.getBranchTip(key)
            when {
                persisted == null -> store.setBranchTip(key, st.entry.newSha)
                persisted != st.entry.newSha -> {
                    LOG.info("Overseer catch-up on ${st.branch} (${persisted.take(8)}..${st.entry.newSha.take(8)})")
                    ReviewLauncher.onCommits(project, repoRoot, st.branch, persisted, st.entry.newSha)
                }
            }
        }
    }

    // Must never throw, or scheduleWithFixedDelay permanently stops the repeating task.
    private fun safeScan() {
        try {
            scan()
        } catch (t: Throwable) {
            LOG.warn("Overseer scan failed for $repoRoot", t)
        }
    }

    private fun scan() {
        for (st in readReflogs()) {
            if (lastSeen[st.file] == st.entry.newSha) continue
            lastSeen[st.file] = st.entry.newSha
            if (!st.entry.isReviewable) {
                LOG.info("Overseer: ${st.branch} -> ${st.entry.newSha.take(8)} skipped (msg='${st.entry.message}')")
                continue
            }
            LOG.info("Overseer detected change on ${st.branch} (${st.entry.oldSha.take(8)}..${st.entry.newSha.take(8)} msg='${st.entry.message.take(20)}')")
            ReviewLauncher.onCommits(project, repoRoot, st.branch, st.entry.oldSha, st.entry.newSha)
        }
    }

    private fun readReflogs(): List<RefState> {
        if (!Files.isDirectory(headsDir)) return emptyList()
        val out = ArrayList<RefState>()
        try {
            Files.walk(headsDir).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { f ->
                    val entry = ReflogParser.parseLast(readText(f)) ?: return@forEach
                    val branch = try {
                        headsDir.relativize(f).toString().replace('\\', '/')
                    } catch (_: Exception) {
                        f.fileName.toString()
                    }
                    out.add(RefState(f, entry, branch))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun readText(p: Path): String = try { Files.readString(p) } catch (_: Exception) { "" }

    companion object {
        private const val POLL_SECONDS = 3L
        private val LOG = logger<RepoWatcher>()
    }
}
