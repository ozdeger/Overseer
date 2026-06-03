#!/usr/bin/env bash
# Publish the built Overseer plugin zip as a GitHub release, tagged v<version>.
#
# Requires the GitHub CLI:  https://cli.github.com/   (run 'gh auth login' once).
# The repo is inferred from your git 'origin' remote; override with the GH_REPO env var,
# e.g.  GH_REPO=ozdeger/Overseer ./release.sh
#
# Reads the version from build.gradle.kts and uploads dist/overseer-<version>.zip.
# If that zip isn't present yet, it runs ./build-dist.sh to produce it first.
set -euo pipefail
cd "$(dirname "$0")"

command -v gh >/dev/null 2>&1 || {
  echo "ERROR: GitHub CLI 'gh' not found. Install from https://cli.github.com/ and run 'gh auth login'." >&2
  exit 1
}

VERSION="$(grep -E '^version *= *"' build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
[ -n "$VERSION" ] || { echo "ERROR: could not read version from build.gradle.kts" >&2; exit 1; }
TAG="v$VERSION"
ZIP="dist/overseer-$VERSION.zip"

# Build if the expected zip isn't there yet.
if [ ! -f "$ZIP" ]; then
  echo ">> $ZIP not found - building it first..."
  ./build-dist.sh
fi
# Fall back to whatever single zip is in dist/ if the name differs.
if [ ! -f "$ZIP" ]; then
  ZIP="$(ls -1 dist/*.zip 2>/dev/null | head -1 || true)"
fi
[ -f "$ZIP" ] || { echo "ERROR: no plugin zip in dist/. Run ./build-dist.sh first." >&2; exit 1; }

echo ">> Publishing release $TAG with $(basename "$ZIP")"

if gh release view "$TAG" >/dev/null 2>&1; then
  echo ">> Release $TAG already exists - updating its asset."
  gh release upload "$TAG" "$ZIP" --clobber
else
  gh release create "$TAG" "$ZIP" \
    --title "Overseer $VERSION" \
    --notes "Overseer $VERSION"
fi

echo
echo ">> Done. Release $TAG published with $(basename "$ZIP")."
echo "   Commit & push updatePlugins.xml so the repo URL serves the new version:"
echo "     git add updatePlugins.xml && git commit -m \"Release $VERSION\" && git push"
