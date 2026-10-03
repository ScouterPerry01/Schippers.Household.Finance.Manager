#!/usr/bin/env bash
# Builds the AppImage (DIST-02) from the Linux app image made by createDistributable.
# Usage: build-appimage.sh <version> <appimagetool> <output folder>
set -euo pipefail
version="$1"; tool="$2"; out="$3"
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../../.." && pwd)"
image="$root/app/desktop/build/compose/binaries/main/app/RANN's Roost"
appdir="$(mktemp -d)/RANNsRoost.AppDir"

mkdir -p "$appdir"
cp -a "$image/." "$appdir/"
cp "$here/ranns-roost.desktop" "$appdir/ranns-roost.desktop"
cp "$root/branding/desktop/ranns-roost.png" "$appdir/ranns-roost.png"
cat > "$appdir/AppRun" <<'RUN'
#!/bin/sh
here="$(dirname "$(readlink -f "$0")")"
exec "$here/bin/RANN's Roost" "$@"
RUN
chmod +x "$appdir/AppRun"

mkdir -p "$out"
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$tool" --no-appstream "$appdir" "$out/ranns-roost-$version-x86_64.AppImage"
