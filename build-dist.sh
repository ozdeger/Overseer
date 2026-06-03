#!/usr/bin/env bash
# Build a distributable Overseer plugin zip and a matching updatePlugins.xml
# for a private custom plugin repository.
#
# Usage:
#   ./build-dist.sh [DOWNLOAD_BASE_URL]
#
#   DOWNLOAD_BASE_URL = where the zip will be hosted (no trailing slash), e.g.
#     https://github.com/ozdeger/Overseer/releases/download/v0.1.0
#   It's written into updatePlugins.xml's <plugin url=...>. You can also set it via
#   the OVERSEER_DOWNLOAD_BASE env var, or just edit dist/updatePlugins.xml afterward.
#
# Output goes to ./dist/ : the plugin zip + updatePlugins.xml.
set -euo pipefail
cd "$(dirname "$0")"

# --- pick a gradle command ---
if [ -x "./gradlew" ]; then
  GRADLE="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="gradle"
else
  echo "ERROR: no ./gradlew wrapper and no 'gradle' on PATH." >&2
  echo "       Install Gradle, or run 'gradle wrapper' once to create ./gradlew." >&2
  exit 1
fi

# --- read plugin coordinates from the project ---
VERSION="$(grep -E '^version *= *"' build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
PLUGIN_ID="$(grep -oE '<id>[^<]+</id>' src/main/resources/META-INF/plugin.xml | head -1 | sed -E 's#</?id>##g')"
SINCE_BUILD="$(grep -E 'sinceBuild *=' build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
: "${SINCE_BUILD:=243}"
[ -n "$VERSION" ]   || { echo "ERROR: could not read version from build.gradle.kts" >&2; exit 1; }
[ -n "$PLUGIN_ID" ] || { echo "ERROR: could not read <id> from plugin.xml" >&2; exit 1; }

echo ">> Building $PLUGIN_ID $VERSION (since-build $SINCE_BUILD)"

# --- build the plugin zip ---
"$GRADLE" clean buildPlugin

# --- locate the produced zip ---
ZIP="$(ls -1 build/distributions/*.zip 2>/dev/null | head -1 || true)"
[ -n "$ZIP" ] || { echo "ERROR: no zip found in build/distributions/" >&2; exit 1; }
ZIP_NAME="$(basename "$ZIP")"

# --- assemble dist/ ---
DOWNLOAD_BASE="${1:-${OVERSEER_DOWNLOAD_BASE:-https://CHANGE-ME/path/to}}"
mkdir -p dist
cp -f "$ZIP" "dist/$ZIP_NAME"

# updatePlugins.xml is written to the repo ROOT so it can be committed and served from a
# stable raw URL. The zip stays in dist/ (gitignored) and is uploaded to your release host.
cat > updatePlugins.xml <<XML
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin id="$PLUGIN_ID"
          url="$DOWNLOAD_BASE/$ZIP_NAME"
          version="$VERSION">
    <name>Overseer</name>
    <description>Claude-powered per-commit code review for Rider.</description>
    <vendor>ozdeger</vendor>
    <idea-version since-build="$SINCE_BUILD" until-build="999.*"/>
  </plugin>
</plugins>
XML

echo
echo ">> Done."
echo "   Plugin zip:      dist/$ZIP_NAME   (upload to your release host)"
echo "   Repo descriptor: updatePlugins.xml   (repo root - commit & push this)"
echo
echo ">> Next steps:"
echo "   1) Upload dist/$ZIP_NAME to your host (e.g. a GitHub Release asset)."
echo "   2) Ensure <plugin url=> in updatePlugins.xml points at that uploaded zip."
echo "      current: $DOWNLOAD_BASE/$ZIP_NAME"
echo "   3) Commit & push updatePlugins.xml. Recipients add its RAW URL via"
echo "      Settings > Plugins > gear > Manage Plugin Repositories."
if [ "$DOWNLOAD_BASE" = "https://CHANGE-ME/path/to" ]; then
  echo
  echo "   NOTE: download URL not set. Re-run as: ./build-dist.sh <DOWNLOAD_BASE_URL>"
fi
