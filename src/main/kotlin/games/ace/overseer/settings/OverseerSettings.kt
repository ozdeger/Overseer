package games.ace.overseer.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** Application-level persisted settings for Overseer. */
@Service(Service.Level.APP)
@State(name = "OverseerSettings", storages = [Storage("overseer.xml")])
class OverseerSettings : PersistentStateComponent<OverseerSettings.State> {

    data class State(
        var promptOverride: String? = null,
        var claudePath: String = "claude",
        var model: String = "claude-opus-4-8",
        var autoReviewOnCommit: Boolean = true,
        var reviewOnlyMyCommits: Boolean = true,
        var allowedExtensions: String = DEFAULT_EXTENSIONS,
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    var promptOverride: String?
        get() = state.promptOverride
        set(v) { state.promptOverride = v }

    var claudePath: String
        get() = state.claudePath
        set(v) { state.claudePath = v }

    var model: String
        get() = state.model
        set(v) { state.model = v }

    var autoReviewOnCommit: Boolean
        get() = state.autoReviewOnCommit
        set(v) { state.autoReviewOnCommit = v }

    var reviewOnlyMyCommits: Boolean
        get() = state.reviewOnlyMyCommits
        set(v) { state.reviewOnlyMyCommits = v }

    var allowedExtensions: String
        get() = state.allowedExtensions
        set(v) { state.allowedExtensions = v }

    /** Parsed, normalized extension allowlist (no dots, lowercased). Empty => review everything. */
    fun allowedExtensionList(): List<String> =
        state.allowedExtensions
            .split(',', ';', ' ', '\n', '\r', '\t')
            .map { it.trim().removePrefix(".").lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** The prompt actually used at review time: override if set, else the bundled default. */
    val activePrompt: String
        get() = state.promptOverride?.takeIf { it.isNotBlank() } ?: defaultPrompt()

    fun resetPromptToDefault() { state.promptOverride = null }

    companion object {
        fun getInstance(): OverseerSettings = service()

        const val DEFAULT_EXTENSIONS =
            "cs, kt, java, js, jsx, ts, tsx, py, go, rs, c, cpp, cc, h, hpp, m, mm, swift, rb, " +
            "php, scala, sh, bash, sql, json, yaml, yml, toml, xml, gradle, kts, md, txt, html, css, scss, vue, dart"

        fun defaultPrompt(): String =
            OverseerSettings::class.java.getResource("/defaults/review-prompt.md")?.readText()
                ?: "Review the following diff for bugs and security issues. End with a line 'VERDICT: OK|INFO|WARNING|ERROR'."
    }
}
