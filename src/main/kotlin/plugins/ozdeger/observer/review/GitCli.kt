package plugins.ozdeger.observer.review

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import java.nio.charset.StandardCharsets

/** A commit's hash, subject, committer time (epoch millis), and committer email. */
data class CommitMeta(val hash: String, val subject: String, val timeMillis: Long, val committerEmail: String)

/** Thin wrapper around the `git` CLI. No dependency on the IDE's Git client. */
object GitCli {

    private const val SEP = '\u001F'

    fun run(repoRoot: String, indicator: ProgressIndicator, vararg args: String): String =
        try {
            val cmd = GeneralCommandLine(listOf("git", *args))
                .withWorkDirectory(repoRoot)
                .withCharset(StandardCharsets.UTF_8)
            CapturingProcessHandler(cmd).runProcessWithProgressIndicator(indicator).stdout
        } catch (e: Exception) {
            LOG.warn("git ${args.joinToString(" ")} failed in $repoRoot", e)
            ""
        }

    /** The configured user email for this repo (empty if unset) — your committer identity. */
    fun userEmail(repoRoot: String, indicator: ProgressIndicator): String =
        run(repoRoot, indicator, "config", "user.email").trim()

    fun commitDiff(repoRoot: String, sha: String, indicator: ProgressIndicator, extensions: List<String>): String {
        val pathspec = if (extensions.isEmpty()) emptyList() else listOf("--") + extensions.map { "*.$it" }
        val diffArgs = (listOf("diff", "--no-color", "$sha^..$sha") + pathspec).toTypedArray()
        val ranged = run(repoRoot, indicator, *diffArgs)
        if (ranged.isNotBlank()) return ranged
        val showArgs = (listOf("show", "--no-color", "--format=", sha) + pathspec).toTypedArray()
        return run(repoRoot, indicator, *showArgs)
    }

    /**
     * Commits introduced by a range, oldest first, with committer time and committer email.
     * Merge commits are excluded. Committer filtering is done by the caller so the limit can be
     * applied after filtering.
     */
    fun commitsForPush(
        repoRoot: String,
        oldSha: String,
        newSha: String,
        indicator: ProgressIndicator,
        limit: Int = 100,
    ): List<CommitMeta> {
        val args = mutableListOf("log", "--no-merges", "--format=%H%x1f%s%x1f%ct%x1f%ce")
        if (oldSha.all { it == '0' }) {
            args += "-1"
            args += newSha
        } else {
            args += "--reverse"
            args += "-n"; args += limit.toString()
            args += "$oldSha..$newSha"
        }
        val raw = run(repoRoot, indicator, *args.toTypedArray())
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(SEP)
                if (parts.size < 4) return@mapNotNull null
                val timeMillis = parts[2].trim().toLongOrNull()?.times(1000L) ?: 0L
                CommitMeta(parts[0], parts[1], timeMillis, parts[3].trim())
            }
            .toList()
    }

    private val LOG = logger<GitCli>()
}
