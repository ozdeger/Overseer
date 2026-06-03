package plugins.ozdeger.observer.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import plugins.ozdeger.observer.review.CommitReview
import plugins.ozdeger.observer.review.OverseerReviewListener
import plugins.ozdeger.observer.review.ReviewLauncher
import plugins.ozdeger.observer.review.ReviewStore
import plugins.ozdeger.observer.review.Verdict
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.HyperlinkEvent

/**
 * Tool window content: a list of reviewed commits (newest commit time first), a markdown detail
 * pane (JCEF), and a chat-style input below it to respond and trigger a re-evaluation.
 */
class OverseerPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val listModel = DefaultListModel<CommitReview>()
    private val list = JBList(listModel)

    private val cef: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val fallback: JEditorPane? = if (cef == null) JEditorPane().apply {
        isEditable = false
        contentType = "text/html"
        editorKit = HTMLEditorKitBuilder().build()
        border = JBUI.Borders.empty(8)
        background = UIUtil.getPanelBackground()
        addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) e.url?.let { BrowserUtil.browse(it) }
        }
    } else null

    private val inputArea = JBTextArea(3, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        emptyText.text = "Respond to re-evaluate this review  (Enter to send, Shift+Enter for newline)"
    }
    private val sendButton = JButton("Send")
    private var lastRenderSig: String? = null

    init {
        cef?.let { Disposer.register(this, it) }

        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = ReviewRenderer()
        list.addListSelectionListener {
            renderSelected()
            updateInputState()
        }
        installContextMenu()

        inputArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "overseer.send")
        inputArea.actionMap.put("overseer.send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = send()
        })
        sendButton.addActionListener { send() }

        val reviewComp: JComponent = cef?.component ?: JBScrollPane(fallback!!)
        val inputPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
            add(JBScrollPane(inputArea), BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }
        val detailPanel = JPanel(BorderLayout()).apply {
            add(reviewComp, BorderLayout.CENTER)
            add(inputPanel, BorderLayout.SOUTH)
        }

        val splitter = OnePixelSplitter(false, 0.38f).apply {
            firstComponent = JBScrollPane(list)
            secondComponent = detailPanel
        }
        add(splitter, BorderLayout.CENTER)

        project.messageBus.connect(this).subscribe(ReviewStore.TOPIC, OverseerReviewListener {
            ApplicationManager.getApplication().invokeLater { refresh() }
        })

        refresh()
    }

    private fun send() {
        val review = list.selectedValue ?: return
        if (review.inProgress) return
        val note = inputArea.text.trim()
        if (note.isEmpty()) return
        inputArea.text = ""
        ReviewLauncher.reEvaluate(project, review, note)
    }

    private fun updateInputState() {
        val r = list.selectedValue
        val enabled = r != null && !r.inProgress
        inputArea.isEnabled = enabled
        sendButton.isEnabled = enabled
    }

    private fun renderSelected() {
        val r = list.selectedValue
        val sig = r?.let { "${it.key}|${it.inProgress}|${it.verdict}|${it.body.hashCode()}" }
        if (sig == lastRenderSig) return
        lastRenderSig = sig
        val md = r?.let { buildMarkdown(it) } ?: ""
        showHtml(MarkdownRenderer.toHtml(md))
    }

    private fun showHtml(html: String) {
        val browser = cef
        if (browser != null) {
            browser.loadHTML(html)
        } else {
            fallback!!.text = html
            fallback.caretPosition = 0
        }
    }

    private fun installContextMenu() {
        val menu = JBPopupMenu()
        val reviewAgain = JMenuItem("Review Again").apply {
            addActionListener {
                list.selectedValue?.let {
                    ReviewLauncher.reviewCommit(project, it.repoPath, it.commitHash, it.subject, it.timestamp)
                }
            }
        }
        val delete = JMenuItem("Delete").apply {
            addActionListener {
                list.selectedValue?.let { ReviewStore.getInstance(project).removeReview(it.key) }
            }
        }
        menu.add(reviewAgain)
        menu.add(delete)

        list.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybeShow(e)
            override fun mouseReleased(e: MouseEvent) = maybeShow(e)
            private fun maybeShow(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val idx = list.locationToIndex(e.point)
                if (idx < 0 || !list.getCellBounds(idx, idx).contains(e.point)) return
                list.selectedIndex = idx
                val notRunning = list.selectedValue?.inProgress == false
                reviewAgain.isEnabled = notRunning
                delete.isEnabled = notRunning
                menu.show(list, e.x, e.y)
            }
        })
    }

    fun refresh() {
        val previouslySelected = list.selectedValue?.key
        listModel.clear()
        ReviewStore.getInstance(project).all().forEach { listModel.addElement(it) }
        if (previouslySelected != null) {
            for (i in 0 until listModel.size()) {
                if (listModel.get(i).key == previouslySelected) { list.selectedIndex = i; break }
            }
        } else if (!listModel.isEmpty) {
            list.selectedIndex = 0
        }
        renderSelected()
        updateInputState()
    }

    fun clearAll() = ReviewStore.getInstance(project).clear()

    private fun buildMarkdown(r: CommitReview): String = buildString {
        append("**Commit** `").append(r.shortHash).append("` - ").append(r.subject).append("\n\n")
        append("**Repo** ").append(r.repoPath).append("\n\n")
        if (r.inProgress) {
            append("**Status** ⏳ Reviewing...\n\n")
        } else {
            append("**Verdict** ").append(r.verdict.emoji).append(' ').append(r.verdict.label).append("\n\n")
            if (r.inputTokens > 0 || r.outputTokens > 0 || r.costUsd > 0.0) {
                append("**Tokens** ")
                append(String.format("%,d", r.inputTokens)).append(" in / ")
                append(String.format("%,d", r.outputTokens)).append(" out")
                if (r.costUsd > 0.0) append(" | ").append(String.format("$%.4f", r.costUsd))
                append("\n\n")
            }
        }
        append("---\n\n")
        append(r.body)
    }

    override fun dispose() {}

    /** Uses native IDE severity icons (crisp + theme-correct), not emoji which mis-render in Swing lists. */
    private class ReviewRenderer : ColoredListCellRenderer<CommitReview>() {
        override fun customizeCellRenderer(
            jList: JList<out CommitReview>,
            value: CommitReview?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            value ?: return
            if (value.inProgress) {
                icon = AllIcons.Actions.Refresh
                append(value.subject, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                return
            }
            icon = when (value.verdict) {
                Verdict.OK -> AllIcons.General.InspectionsOK
                Verdict.INFO -> AllIcons.General.Information
                Verdict.WARNING -> AllIcons.General.Warning
                Verdict.ERROR -> AllIcons.General.Error
            }
            append(value.subject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
    }
}
