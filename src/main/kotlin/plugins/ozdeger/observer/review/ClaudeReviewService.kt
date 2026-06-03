package plugins.ozdeger.observer.review

import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.progress.ProgressIndicator
import plugins.ozdeger.observer.settings.OverseerSettings
import java.io.File
import java.nio.charset.StandardCharsets

data class ReviewRequest(val repoRoot: String, val ref: String, val diff: String)
data class ReviewResult(
    val verdict: Verdict,
    val output: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val costUsd: Double = 0.0,
)

/**
 * Invokes the local Claude Code CLI to review a diff. Uses --output-format json to read token
 * usage and cost. The diff (and any [extraContext], e.g. a re-evaluation note) are fed on stdin
 * via a temp file, so large input doesn't hit the OS command-line length limit.
 */
object ClaudeReviewService {

    fun review(req: ReviewRequest, indicator: ProgressIndicator, extraContext: String? = null): ReviewResult {
        val settings = OverseerSettings.getInstance()

        val instruction = settings.activePrompt
        val stdinText = buildString {
            append("--- GIT DIFF (").append(req.ref).append(") ---\n")
            append(req.diff)
            if (!extraContext.isNullOrBlank()) {
                append("\n\n").append(extraContext)
            }
        }

        val args = mutableListOf(ExecutableResolver.resolve(settings.claudePath))
        if (settings.model.isNotBlank()) {
            args += "--model"
            args += settings.model.trim()
        }
        args += "-p"
        args += instruction
        args += "--output-format"
        args += "json"

        val tmp = File.createTempFile("overseer-diff-", ".txt")
        return try {
            tmp.writeText(stdinText, StandardCharsets.UTF_8)

            val cmd = GeneralCommandLine(args)
                .withWorkDirectory(req.repoRoot)
                .withCharset(StandardCharsets.UTF_8)
                .withInput(tmp)

            indicator.text = "Overseer: reviewing ${req.ref}..."

            val output = CapturingProcessHandler(cmd).runProcessWithProgressIndicator(indicator)
            if (output.exitCode != 0) {
                val err = output.stderr.ifBlank { output.stdout }.trim()
                return ReviewResult(Verdict.ERROR, err.ifBlank { "(no output from claude)" })
            }

            val stdout = output.stdout.trim()
            parseJson(stdout) ?: run {
                val text = stdout.ifBlank { "(no output from claude)" }
                ReviewResult(verdictOf(text), text)
            }
        } finally {
            tmp.delete()
        }
    }

    private fun parseJson(raw: String): ReviewResult? = try {
        val obj = JsonParser.parseString(raw).asJsonObject
        val result = obj.get("result")?.takeIf { !it.isJsonNull }?.asString ?: return null
        val usage = obj.getAsJsonObject("usage")
        fun u(k: String): Int = usage?.get(k)?.takeIf { !it.isJsonNull }?.asInt ?: 0
        val inTok = u("input_tokens") + u("cache_creation_input_tokens") + u("cache_read_input_tokens")
        val outTok = u("output_tokens")
        val cost = obj.get("total_cost_usd")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
        ReviewResult(verdictOf(result), result, inTok, outTok, cost)
    } catch (e: Exception) {
        null
    }

    private fun verdictOf(text: String): Verdict = when {
        text.contains("VERDICT: ERROR", ignoreCase = true) -> Verdict.ERROR
        text.contains("VERDICT: WARNING", ignoreCase = true) -> Verdict.WARNING
        text.contains("VERDICT: INFO", ignoreCase = true) -> Verdict.INFO
        text.contains("VERDICT: OK", ignoreCase = true) -> Verdict.OK
        else -> Verdict.WARNING
    }
}
