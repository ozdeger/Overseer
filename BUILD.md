# Building & running Overseer

A pure-JVM IntelliJ-platform plugin targeting Rider. The build downloads the Rider SDK
(~1.5 GB on first run) from JetBrains, so it must run on your machine with internet access —
it can't be built inside the assistant's sandbox (blocked repos + JDK 11 there).

## Prerequisites

- **JDK 21** (Rider 2024.3 runs on JBR 21). Verify: `java -version` shows 21.
  - Easiest: [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) or the JetBrains Runtime 21.
- **Gradle 8.10+** *or* IntelliJ IDEA (which ships a bundled Gradle). You do **not** need a separate
  Rider install to build — `runIde` downloads a sandbox Rider for you.

> No Gradle wrapper is committed yet (the wrapper `.jar` can't be generated in the sandbox).
> Step 1 below creates it; after that you can use `./gradlew` as normal.

## One-time setup: generate the Gradle wrapper

From the project folder (`...\Overseer Plugin`):

```bash
gradle wrapper --gradle-version 8.10.2
```

This writes `gradlew`, `gradlew.bat`, and `gradle/wrapper/*`. (If you open the folder in
IntelliJ IDEA instead, it imports the Gradle project and creates the wrapper automatically —
skip this step.)

## Run it in a sandbox Rider

```bash
./gradlew runIde          # Windows: gradlew.bat runIde
```

First run downloads the Rider SDK and launches a clean Rider with Overseer installed. Then:

1. Open any Git project that has a remote configured.
2. Push some commits — **from anywhere**: Rider's UI, or just `git push` in a terminal inside
   that repo. Overseer watches the `.git` reflogs, so it doesn't matter how you push.
3. Open the **Overseer** tool window (right edge): each pushed commit gets a verdict
   (`PASS` / `ISSUES`); click one to read the review.
4. If a review fails (e.g. `claude` not found), you get an **error notification** with a
   "Show in Overseer" action.

> Detection relies on git reflogs, which are on by default. If nothing shows up, confirm
> `git config core.logAllRefUpdates` isn't `false`, and that the push actually updated a
> remote-tracking branch (`git log -1 .git/logs/refs/remotes/<remote>/<branch>`).

Make sure the `claude` CLI is on your PATH, or set its path in **Settings → Tools → Overseer**.

## Package an installable zip

```bash
./gradlew buildPlugin
```

Output: `build/distributions/overseer-0.1.0.zip`. Install in any Rider 2024.3+ via
**Settings → Plugins → ⚙ → Install Plugin from Disk…**

## Common first-build issues

- **`Unsupported class file major version` / toolchain errors** → you're not on JDK 21. Point
  `JAVA_HOME` at a JDK 21.
- **`Could not resolve ... Rider`** → network/proxy blocking `cache-redirector.jetbrains.com`.
- **Kotlin/IntelliJ API mismatch** → if you bump `rider("2024.3")` to a much newer build, an API
  signature may have changed; paste the error and it's usually a one-line fix.

If anything fails to compile, paste the Gradle output and I'll patch it.
