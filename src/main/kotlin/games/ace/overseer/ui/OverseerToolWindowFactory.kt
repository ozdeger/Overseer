package games.ace.overseer.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import games.ace.overseer.settings.OverseerConfigurable

/** Registers the "Overseer" tool window where users browse per-commit reviews. */
class OverseerToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = OverseerPanel(project)

        val refresh = object : AnAction("Refresh", "Reload reviews", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) = panel.refresh()
        }
        val clear = object : AnAction("Clear All Reviews", "Delete all stored reviews", AllIcons.Actions.GC) {
            override fun actionPerformed(e: AnActionEvent) = panel.clearAll()
        }
        val settings = object : AnAction("Overseer Settings", "Open Overseer settings", AllIcons.General.Settings) {
            override fun actionPerformed(e: AnActionEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, OverseerConfigurable::class.java)
            }
        }
        toolWindow.setTitleActions(listOf(refresh, clear, settings))

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}
