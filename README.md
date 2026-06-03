# 👁 Overseer

**Automatic, AI-powered code review on every commit — right inside JetBrains Rider.**

Overseer quietly watches your repository and, whenever you make a commit, reviews it with your local
[Claude Code](https://docs.claude.com/en/docs/claude-code) (`claude`) CLI. Each commit gets an
advisory verdict and a written review you can browse any time from the **Overseer** tool window.
Nothing is ever blocked and nothing leaves your machine except the diff sent to Claude — it's a
second pair of eyes that keeps a running record for you to read later.

It works independently of the IDE's Git client, so it catches your commits, amends, cherry-picks and
merges no matter how you make them — and it reviews **only your own commits**, so teammates' work
pulled in via merges doesn't get re-reviewed.

---

## What it looks like

The **Overseer** tool window (right edge) lists your reviewed commits, each with a severity icon and
the commit subject. Selecting one shows the full review rendered as markdown, along with its verdict
and the tokens/cost it used. A chat box under the review lets you push back and have Claude
re-evaluate.

| Verdict | Meaning |
|---|---|
| ✅ **OK** | No issues. |
| ℹ️ **INFO** | Minor notes / nits / suggestions. |
| ⚠️ **WARNING** | Issues worth addressing. |
| 🔴 **ERROR** | Serious problems (bugs, security, data loss) — or the review couldn't run. |

## Features

- **Reviews every commit, automatically** — catches commits, amends, cherry-picks and rebases
  however you make them, and catches up on commits made while the IDE was closed.
- **Only your commits** — filtered by committer identity. Cherry-picks you apply are reviewed;
  teammates' commits merged/pulled in are skipped. Toggleable.
- **Advisory, never blocking** — reviews are a record you read on your own time, not a gate.
- **Per-commit history** — every review is stored and browsable, ordered by commit time.
- **Re-evaluate with a reply** — disagree with a finding? Type a response and Claude reconsiders
  with your note as context; the back-and-forth is kept as a thread.
- **Token & cost tracking** per review.
- **File-extension allowlist** — only source/text files are sent for review; binaries and assets
  (`.png`, `.meta`, `.prefab`, …) are skipped, so you don't spend tokens on noise.
- **Configurable** Claude model, review prompt, and `claude`/`git` paths.

## Requirements

- JetBrains **Rider 2024.3** or newer.
- Anthropic's **`claude` CLI** installed and authenticated — on your `PATH`, or with its full path
  set in Overseer's settings. See the [Claude Code docs](https://docs.claude.com/en/docs/claude-code).

## Install

### From the plugin repository (recommended — gets automatic updates)

1. In Rider: **Settings → Plugins → ⚙ → Manage Plugin Repositories…**
2. Add this URL:
   ```
   https://raw.githubusercontent.com/ozdeger/Overseer/master/updatePlugins.xml
   ```
3. Go to the **Marketplace** tab, search **Overseer**, and click **Install**.

Rider will notify you when a new version is available.

### From a downloaded file

Download the latest `overseer-x.y.z.zip` from the
[Releases page](https://github.com/ozdeger/Overseer/releases), then in Rider go to
**Settings → Plugins → ⚙ → Install Plugin from Disk…** and select the zip.

## Getting started

1. Make sure the `claude` CLI is available (`which claude`, or `where claude` on Windows). If it
   isn't on your `PATH`, set its full path in **Settings → Tools → Overseer**.
2. Open the **Overseer** tool window (right edge, or **View → Tool Windows → Overseer**).
3. Commit as usual. Within a few seconds the commit appears in the list and gets reviewed.
4. Click a commit to read its review. To challenge a finding, type a reply in the box below the
   review and press **Enter** — Claude re-evaluates with your note in mind.
5. Right-click a commit for **Review Again** or **Delete**.

## Settings

Everything is under **Settings → Tools → Overseer**:

- **Claude model** — which model to use (default `claude-opus-4-8`).
- **Path to claude / git** — leave blank to use your `PATH`, or set full paths.
- **Automatically review each new commit** — turn auto-review on/off.
- **Only review my own commits** — skip commits committed by others.
- **Reviewed file extensions** — only these file types are sent for review.
- **Review prompt** — the instructions Claude follows (with a button to restore the default).

## Privacy

Overseer runs entirely on your machine using your own `claude` CLI. The only data sent anywhere is
the commit diff (and any re-evaluation notes) passed to Claude through that CLI. Reviews are stored
locally in your project and are never uploaded.

## Notes

Overseer is an independent project and is not affiliated with JetBrains or Anthropic.
"Claude" and "Claude Code" are products of Anthropic.
