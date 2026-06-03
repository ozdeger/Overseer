package plugins.ozdeger.observer.review

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/** Fires IDE notifications for Overseer. Safe to call from any thread. */
object OverseerNotifier {

    private const val GROUP_ID = "Overseer"
    private const val TOOL_WINDOW_ID = "Overseer"

    /** A balloon (also surfaced as an OS notification when the IDE isn't focused) for a failed review. */
    fun notifyReviewError(project: Project, review: CommitReview) {
        val detail = review.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(200).orEmpty()
        val content = "Couldn't review ${review.shortHash} - ${review.subject}" +
            (if (detail.isNotBlank()) "\n$detail" else "")

        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification("Overseer: review error", content, NotificationType.ERROR)
            .addAction(object : NotificationAction("Show in Overseer") {
                override fun actionPerformed(e: AnActionEvent, notification: com.intellij.notification.Notification) {
                    ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.activate(null)
                    notification.expire()
                }
            })
            .notify(project)
    }

    /** Generic error notification (e.g. a review job failed before it could record a result). */
    fun notifyError(project: Project, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification("Overseer: review failed", content, NotificationType.ERROR)
            .notify(project)
    }
}
