package plugins.ozdeger.observer.watch

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Starts the .git push watcher when a project finishes opening. */
class OverseerStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        GitPushWatcherService.getInstance(project).start()
    }
}
