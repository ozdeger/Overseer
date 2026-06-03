package plugins.ozdeger.observer.watch

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Discovers the git repositories in the project (via platform content roots, not the Git client)
 * and runs a [RepoWatcher] for each. Disposed automatically when the project closes.
 */
@Service(Service.Level.PROJECT)
class GitPushWatcherService(private val project: Project) : Disposable {

    private val watchers = mutableListOf<RepoWatcher>()
    @Volatile private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        for (gitDir in discoverGitDirs()) {
            try {
                RepoWatcher(project, gitDir).also { it.start(); watchers.add(it) }
            } catch (e: Exception) {
                LOG.warn("Overseer: failed to watch $gitDir", e)
            }
        }
        if (watchers.isEmpty()) LOG.info("Overseer: no git repositories found to watch")
    }

    private fun discoverGitDirs(): Set<Path> {
        val candidates = LinkedHashSet<Path>()
        project.basePath?.let { candidates.add(Path.of(it)) }
        ProjectRootManager.getInstance(project).contentRoots.forEach { candidates.add(Path.of(it.path)) }

        val gitDirs = LinkedHashSet<Path>()
        for (root in candidates) {
            val dotGit = root.resolve(".git")
            when {
                Files.isDirectory(dotGit) -> gitDirs.add(dotGit)
                Files.isRegularFile(dotGit) -> resolveGitFile(dotGit)?.let { gitDirs.add(it) }
            }
        }
        return gitDirs
    }

    private fun resolveGitFile(dotGitFile: Path): Path? = try {
        val line = Files.readAllLines(dotGitFile).firstOrNull { it.startsWith("gitdir:") } ?: return null
        val target = Path.of(line.removePrefix("gitdir:").trim())
        val resolved = if (target.isAbsolute) target else dotGitFile.parent.resolve(target).normalize()
        if (Files.isDirectory(resolved)) resolved else null
    } catch (_: Exception) {
        null
    }

    override fun dispose() {
        watchers.forEach { it.stop() }
        watchers.clear()
    }

    companion object {
        fun getInstance(project: Project): GitPushWatcherService = project.service()
        private val LOG = logger<GitPushWatcherService>()
    }
}
