# Overseer

A JetBrains Rider plugin that runs a Claude-powered code review on every commit you make.

Overseer watches your repository's git reflogs directly (independent of the IDE's Git client),
reviews each new commit with the local `claude` CLI, and records an advisory verdict you can
browse per commit in the Overseer tool window. Reviews never block anything — they are a record
the author reads later.

## Features

- **Commit detection without the IDE's Git client** — polls `.git/logs/refs/heads` reflogs, so it
  catches commits/cherry-picks/merges however they're made, and catches up on commits made while
  the IDE was closed.
- **Per-commit reviews** with four severity levels (OK / INFO / WARNING / ERROR), shown with native
  status icons in a tool window and a JCEF-rendered markdown detail pane.
- **Only your own commits** — filters by committer identity (cherry-picks you apply are included;
  teammates' merged/pulled commits are skipped). Toggleable.
- **File-extension allowlist** — only listed source/text file types are sent for review
  (skips binaries/assets like `.png`, `.meta`, `.prefab`).
- **Parallel reviews** (bounded pool), with in-progress rows, token/cost reporting per review.
- **Re-evaluation** — respond to a review in a chat-style input to have Claude reconsider with
  your note as context; the exchange is appended as a thread.
- Configurable Claude model, prompt, and `claude` CLI path.

## Requirements

- JetBrains Rider 2024.3+
- JDK 21 (to build)
- The `claude` CLI installed and on PATH (or its path set in settings)

## Build & run

```bash
./gradlew runIde      # launches a sandbox Rider with the plugin
./gradlew buildPlugin  # produces an installable zip in build/distributions/
```

See `BUILD.md` for prerequisites and `DESIGN.md` for architecture.

## Settings

**Settings → Tools → Overseer**: Claude model, `claude` CLI path, auto-review toggle,
"only my commits" toggle, reviewed file extensions, and the review prompt.
