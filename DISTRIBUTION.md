# Distributing Overseer privately (custom plugin repository)

Recipients add one URL in Rider; they then get installs + updates like the Marketplace, but the
files live wherever you host them. Nothing goes through JetBrains and there's no review/wait.

You publish two files to a URL only your people know:
- `overseer-<version>.zip` — the built plugin
- `updatePlugins.xml` — a small index pointing at the zip

## One-time setup

### 1. Build the plugin zip (on your machine)
```
cd "C:\Users\QP\Desktop\OverseerPlugin\Overseer Plugin"
gradlew buildPlugin
```
Output: `build/distributions/overseer-0.1.0.zip`.

### 2. Host the files
Pick a place only your people can reach. Easiest options:

**A. GitHub Releases (a separate repo, e.g. `overseer-dist`)**
1. Create the repo (public = "private by obscure URL"; private = most secure but raw URLs need
   tokens, so prefer an internal server if you need true access control).
2. Create a Release tagged `v0.1.0` and upload `overseer-0.1.0.zip` as an asset.
3. Copy the asset's download URL and paste it into `updatePlugins.xml` as the `url=`.
4. Commit `updatePlugins.xml` to the repo; its **raw** URL is what you share, e.g.
   `https://raw.githubusercontent.com/YOUR-USER/overseer-dist/main/updatePlugins.xml`

**B. Internal/company web server (truly private, behind VPN/auth)**
Put both files on the server; share the `updatePlugins.xml` URL. Access control = whatever
protects that server.

### 3. Edit `updatePlugins.xml`
Set `url=` to the actual zip download URL, and keep `version` matching the build.
`id` (`plugins.ozdeger.observer`) and `since-build` (`243`) are already correct.

## What your people do (once)
1. Settings → Plugins → gear icon ⚙ → **Manage Plugin Repositories…**
2. Add the `updatePlugins.xml` URL.
3. Plugins → **Marketplace** tab → search "Overseer" → Install. (It comes from your repo.)

They'll get an update notification automatically whenever you bump the version.

## Shipping an update
1. Bump `version` in `build.gradle.kts` (e.g. `0.1.1`).
2. `gradlew buildPlugin` → new `overseer-0.1.1.zip`.
3. Upload the new zip; update `url=` and `version=` in `updatePlugins.xml`.
4. Done — recipients see "Update available" for Overseer.
