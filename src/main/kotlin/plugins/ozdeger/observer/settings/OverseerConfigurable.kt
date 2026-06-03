package plugins.ozdeger.observer.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

/** Settings page registered at Settings | Tools | Overseer. */
class OverseerConfigurable : Configurable {

    private var ui: OverseerSettingsComponent? = null

    override fun getDisplayName(): String = "Overseer"

    override fun createComponent(): JComponent {
        val component = OverseerSettingsComponent()
        ui = component
        reset()
        return component.panel
    }

    override fun isModified(): Boolean {
        val c = ui ?: return false
        val s = OverseerSettings.getInstance()
        return c.claudePath != s.claudePath ||
            c.model != s.model ||
            c.autoReviewOnCommit != s.autoReviewOnCommit ||
            c.reviewOnlyMyCommits != s.reviewOnlyMyCommits ||
            c.allowedExtensions != s.allowedExtensions ||
            c.promptText != s.activePrompt
    }

    override fun apply() {
        val c = ui ?: return
        val s = OverseerSettings.getInstance()
        s.claudePath = c.claudePath
        s.model = c.model
        s.autoReviewOnCommit = c.autoReviewOnCommit
        s.reviewOnlyMyCommits = c.reviewOnlyMyCommits
        s.allowedExtensions = c.allowedExtensions
        s.promptOverride = if (c.promptText == OverseerSettings.defaultPrompt()) null else c.promptText
    }

    override fun reset() {
        val c = ui ?: return
        val s = OverseerSettings.getInstance()
        c.claudePath = s.claudePath
        c.model = s.model
        c.autoReviewOnCommit = s.autoReviewOnCommit
        c.reviewOnlyMyCommits = s.reviewOnlyMyCommits
        c.allowedExtensions = s.allowedExtensions
        c.promptText = s.activePrompt
    }

    override fun disposeUIResources() { ui = null }
}
