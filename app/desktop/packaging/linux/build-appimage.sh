#!/usr/bin/env bash
# Builds the AppImage (DIST-02) from the Linux app image made by createDistributable.
# Usage: build-appimage.sh <version> <appimagetool> <output folder>
set -euo pipefail
version="$1"; tool="$2"; out="$3"
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../../.." && pwd)"
# The app image is the only folder there (named "RANN’s Roost" on Linux, see build.gradle.kts).
image="$(find "$root/app/desktop/build/compose/binaries/main/app" -mindepth 1 -maxdepth 1 -type d | head -1)"
appdir="$(mktemp -d)/RANNsRoost.AppDir"

mkdir -p "$appdir"
cp -a "$image/." "$appdir/"
cp "$here/ranns-roost.desktop" "$appdir/ranns-roost.desktop"
cp "$root/branding/desktop/ranns-roost.png" "$appdir/ranns-roost.png"
cat > "$appdir/AppRun" <<'RUN'
#!/bin/sh
here="$(dirname "$(readlink -f "$0")")"
exec "$(find "$here/bin" -maxdepth 1 -type f -perm -u+x | head -1)" "$@"
RUN
chmod +x "$appdir/AppRun"

mkdir -p "$out"
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$tool" --no-appstream "$appdir" "$out/ranns-roost-$version-x86_64.AppImage"
