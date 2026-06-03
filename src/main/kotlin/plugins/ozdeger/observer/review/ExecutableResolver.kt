package plugins.ozdeger.observer.review

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * Resolves a configured command (e.g. "git" / "claude") to an executable path.
 *
 * macOS GUI apps launched from the Dock often don't inherit the login-shell PATH, so a bare
 * command name may not be found even though it's installed. We first try the IDE's PATH, then
 * fall back to common install locations on macOS/Linux. An explicit path is used verbatim.
 */
object ExecutableResolver {

    fun resolve(configured: String): String {
        val c = configured.trim()
        if (c.isEmpty()) return c
        if (c.contains('/') || c.contains('\\')) return c // explicit path, use as-is

        PathEnvironmentVariableUtil.findInPath(c)?.let { if (it.canExecute()) return it.absolutePath }

        if (!SystemInfo.isWindows) {
            val home = System.getProperty("user.home").orEmpty()
            val dirs = listOf(
                "/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/bin",
                "$home/.local/bin", "$home/bin", "$home/.bun/bin"
            )
            for (d in dirs) {
                val f = File(d, c)
                if (f.canExecute()) return f.absolutePath
            }
        }
        return c // fall back to the bare name (GeneralCommandLine will try the process PATH)
    }
}
