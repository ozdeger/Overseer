# Overseer — Rider plugin port of ClaudeReviewer

Port of the ClaudeReviewer desktop app into a JetBrains Rider plugin. When commits are pushed, Claude reviews each one and records a verdict. **Nothing is ever blocked.** Reviews are stored per commit and read whenever you want from the Overseer tool window.

A hard constraint shapes the design: **Overseer does not use Rider's Git client (git4idea) at all.** It detects pushes by watching the `.git` filesystem directly, so it works no matter how you push — Rider's UI, the terminal, or any external git client. The plugin depends only on the IntelliJ platform; there is no `Git4Idea` dependency.

## Push detection: watching `.git` reflogs

When `git push` succeeds, git updates the local **remote-tracking ref** (`refs/remotes/<remote>/<branch>`) and appends a line to that ref's reflog at `.git/logs/refs/remotes/<remote>/<branch>`. The reflog message is the key: git writes **`update by push`** for a push, versus `fetch …` / `pull …` for a fetch. Reading that message is how we tell a push apart from a fetch — the one ambiguity of a pure-filesystem approach, resolved.

A reflog line looks like:

```
<old-sha> <new-sha> <name> <email> <ts> <tz>\tupdate by push
```

So each push hands us the old tip, the new tip, and the ref name — enough to enumerate exactly the commits that went out.

### Mechanism

- `GitPushWatcherService` (project service) discovers the repos in the project using **platform content roots** (`ProjectRootManager`) plus the project base dir — no Git-client API. For each, it finds the real `.git` dir (handling the `.git`-as-a-file case for worktrees/submodules) and starts a `RepoWatcher`.
- `RepoWatcher` uses a `java.nio.file.WatchService` on the repo's `.git/logs` subtree (registering subdirectories as `refs/`, `remotes/`, `<remote>/`, and branch dirs appear). On startup it records the current tip of each remote reflog (the *baseline*) so opening a project doesn't re-review history.
- When a remote-tracking reflog file changes, `ReflogParser` reads the last line. If the message is a push and the new sha is one we haven't handled, the watcher calls `ReviewLauncher`.
- `ReviewLauncher` enumerates the pushed commits with the `git` CLI (`git log <old>..<new>`, or the tip commit for a first push), pre-registers them as `PENDING` in the store, and runs `ReviewWorker` in a background `Task`.

This is entirely decoupled from the IDE's VCS subsystem. The trade-off vs. a git hook is that it relies on reflogs being enabled (the default for non-bare repos) and detects the push just after it completes rather than intercepting it — which is exactly what we want for an advisory, non-blocking tool.

## How a push flows

1. You push (any client). git updates `.git/logs/refs/remotes/<remote>/<branch>` with an `update by push` entry.
2. `RepoWatcher` sees the file change, `ReflogParser` confirms it's a push, and reads `old..new`.
3. `ReviewLauncher` lists the pushed commits, marks them `PENDING` (visible immediately in the tool window), and starts a background review.
4. `ReviewWorker` diffs each commit (`git diff <sha>^..<sha>`, `git show` for a root commit) and runs the local `claude` CLI via `GeneralCommandLine`.
5. Each result is saved (verdict `PASS` / `ISSUES` / `ERROR`) and the tool window refreshes live. An `ERROR` fires an IDE balloon (also an OS notification when Rider isn't focused) with a "Show in Overseer" action.
6. You browse commits and read their reviews in the Overseer tool window anytime.

## Feature mapping (desktop → plugin)

| ClaudeReviewer (desktop) | Overseer (Rider plugin) |
|---|---|
| File-based `pushreview` git hook | `.git` reflog **filesystem watcher** (no hooks, no Git client) |
| Hook shells out to `claude` | `ClaudeReviewService` runs `claude` via `GeneralCommandLine`, per commit |
| (blocking semantics) | **None** — push always proceeds; reviews are advisory records |
| Review shown in app window | `ReviewStore` (persisted per commit) + **Overseer tool window** |
| Prompt/config in `%AppData%` | Prompt + settings in a `PersistentStateComponent`; bundled default in resources |
| Edit / Reset in gear menu | Settings page (Tools → Overseer): editable prompt + "Restore default" |
| `OverwriteCompanion` syncing defaults | **Removed** |

## Project shape (pure JVM plugin — no .NET backend, no Git4Idea)

```
Overseer Plugin/
├── build.gradle.kts          # IntelliJ Platform Gradle Plugin 2.16.0, targets Rider 2024.3
├── settings.gradle.kts
├── gradle.properties
├── src/main/kotlin/games/ace/overseer/
│   ├── watch/
│   │   ├── OverseerStartupActivity.kt  # ProjectActivity → starts the watcher on project open
│   │   ├── GitPushWatcherService.kt    # discovers repos (content roots), runs a RepoWatcher each
│   │   ├── RepoWatcher.kt              # WatchService over .git/logs/refs/remotes
│   │   └── ReflogParser.kt             # parses reflog lines, detects "update by push"
│   ├── review/
│   │   ├── ReviewLauncher.kt           # push event → enumerate commits → background review
│   │   ├── ReviewWorker.kt             # diff + review each commit, store results
│   │   ├── ClaudeReviewService.kt      # runs the claude CLI, classifies verdict
│   │   ├── GitCli.kt                   # thin `git` CLI wrapper (no IDE Git client)
│   │   ├── ReviewStore.kt              # @Service(PROJECT) PersistentStateComponent + MessageBus topic
│   │   ├── OverseerNotifier.kt         # ERROR notifications
│   │   └── CommitReview.kt             # data model + Verdict enum
│   ├── settings/                       # OverseerSettings / Configurable / Component
│   └── ui/                             # OverseerToolWindowFactory + OverseerPanel
└── src/main/resources/
    ├── META-INF/plugin.xml            # postStartupActivity, projectServices, toolWindow, notificationGroup, configurable
    └── defaults/review-prompt.md      # bundled default prompt (verdict: PASS / ISSUES)
```

## Build & run

- **Toolchain:** JDK 21 (Rider 2024.3 runs on JBR 21), Kotlin jvmTarget 21.
- **Gradle plugin:** `org.jetbrains.intellij.platform` `2.16.0`, `instrumentCode = false`, `rider("2024.3")`. No `bundledPlugin("Git4Idea")`.
- **Run in Rider:** `./gradlew runIde` launches a sandbox Rider with the plugin. Open a git project; pushing from anywhere (even an external terminal in that repo) produces reviews in the Overseer tool window.
- **Package:** `./gradlew buildPlugin` → installable zip in `build/distributions/`.

## Known limitations / next steps

- **Reflogs must be enabled** (`core.logAllRefUpdates`, the default for non-bare repos). If disabled, pushes aren't detected. Could add a fallback that snapshots `refs/remotes` tips on a timer.
- **First push of a branch** (old sha all-zero) reviews only the pushed tip commit, since the pre-push range can't be reconstructed after the fact.
- **Repo discovery** uses content roots opened at startup; repos added mid-session aren't picked up until reopen. Could subscribe to root changes.
- **On-demand review:** a right-click "Review this commit" action would complement the automatic push detection.
- **Large diffs:** switch the `claude` call from a `-p` argument to stdin/temp-file to dodge command-line length limits.
```
