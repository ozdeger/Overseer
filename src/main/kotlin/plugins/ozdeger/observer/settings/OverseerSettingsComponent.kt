package plugins.ozdeger.observer.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea

/** Swing UI for the Overseer settings page. */
class OverseerSettingsComponent {

    private val claudePathField = JBTextField()
    private val modelField = JBTextField().apply {
        emptyText.text = "default (blank); e.g. opus, sonnet, haiku, or a full model id"
    }
    private val autoReviewCheck =
        JBCheckBox("Automatically review each new commit (reviews are advisory - nothing is blocked)")
    private val onlyMineCheck =
        JBCheckBox("Only review my own commits (skip teammates' commits brought in by merges/pulls)")
    private val extensionsArea = JTextArea(3, 80).apply { lineWrap = true; wrapStyleWord = true }
    private val extensionsResetButton = JButton("Restore default extensions")
    private val promptArea = JTextArea(14, 80).apply { lineWrap = true; wrapStyleWord = true }
    private val resetButton = JButton("Restore default prompt")

    val panel: JPanel

    init {
        resetButton.addActionListener { promptArea.text = OverseerSettings.defaultPrompt() }
        extensionsResetButton.addActionListener { extensionsArea.text = OverseerSettings.DEFAULT_EXTENSIONS }
        val promptScroll = JBScrollPane(promptArea).apply { preferredSize = Dimension(700, 280) }
        val extensionsScroll = JBScrollPane(extensionsArea).apply { preferredSize = Dimension(700, 64) }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Path to claude CLI:"), claudePathField, 1, false)
            .addLabeledComponent(JBLabel("Claude model:"), modelField, 1, false)
            .addComponent(autoReviewCheck, 1)
            .addComponent(onlyMineCheck, 1)
            .addComponent(JBLabel("Reviewed file extensions (comma-separated; files with other extensions are skipped):"), 1)
            .addComponent(extensionsScroll, 1)
            .addComponent(extensionsResetButton, 1)
            .addComponent(JBLabel("Review prompt:"), 1)
            .addComponent(promptScroll, 1)
            .addComponent(resetButton, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        panel.border = JBUI.Borders.empty(10)
    }

    var claudePath: String
        get() = claudePathField.text.trim()
        set(v) { claudePathField.text = v }

    var model: String
        get() = modelField.text.trim()
        set(v) { modelField.text = v }

    var autoReviewOnCommit: Boolean
        get() = autoReviewCheck.isSelected
        set(v) { autoReviewCheck.isSelected = v }

    var reviewOnlyMyCommits: Boolean
        get() = onlyMineCheck.isSelected
        set(v) { onlyMineCheck.isSelected = v }

    var allowedExtensions: String
        get() = extensionsArea.text
        set(v) { extensionsArea.text = v }

    var promptText: String
        get() = promptArea.text
        set(v) { promptArea.text = v }
}
