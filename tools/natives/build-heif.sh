#!/usr/bin/env bash
# Builds libhfmheif.so (HEIC photos on the desktop, ADR 0004) on Linux x86_64 from the pinned
# libheif and libde265 sources in heif.env. Run it in the manylinux_2_28 container, so the library
# needs only glibc 2.28 (2018) or later:
#
#   docker run --rm -v "$PWD:/src" -v "$JAVA_HOME:/jdk:ro" -e JAVA_HOME=/jdk \
#     quay.io/pypa/manylinux_2_28_x86_64 /src/tools/natives/build-heif.sh
#
# It needs a C++20 compiler, CMake, curl and JAVA_HOME pointing to a JDK (for jni.h).
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
out="${1:-$here/../../core/ocr-desktop/src/main/resources/hfm/heif/native/Linux/x86_64}"
work="${WORK:-/tmp/hfm-heif-build}"
# shellcheck disable=SC1091
source "$here/heif.env"
: "${JAVA_HOME:?JAVA_HOME must point to a JDK}"

mkdir -p "$work"
prefix="$work/prefix"
common=(-DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON "-DCMAKE_INSTALL_PREFIX=$prefix" -DCMAKE_INSTALL_LIBDIR=lib)

source_of() { # name version sha256
    local file="$work/$1-$2.tar.gz"
    [ -f "$file" ] || curl -fsSL -o "$file" "https://github.com/strukturag/$1/releases/download/v$2/$1-$2.tar.gz"
    echo "$3  $file" | sha256sum -c - >&2
    tar -xzf "$file" -C "$work"
    echo "$work/$1-$2"
}

de265="$(source_of libde265 "$LIBDE265_VERSION" "$LIBDE265_SHA256")"
cmake -S "$de265" -B "$work/build-de265" "${common[@]}" -DENABLE_SDL=OFF -DENABLE_DECODER=OFF -DENABLE_ENCODER=OFF
cmake --build "$work/build-de265" --target install -j "$(nproc)"

# Only the HEVC decoder (libde265, built in): no encoders, other codecs, plugins or tools.
heif="$(source_of libheif "$LIBHEIF_VERSION" "$LIBHEIF_SHA256")"
off=()
for w in X265 KVAZAAR UVG266 VVDEC VVENC X264 OpenH264_DECODER DAV1D AOM_DECODER AOM_ENCODER SvtEnc RAV1E \
         JPEG_DECODER JPEG_ENCODER OpenJPEG_DECODER OpenJPEG_ENCODER FFMPEG_DECODER OPENJPH_ENCODER UNCOMPRESSED_CODEC \
         LIBSHARPYUV EXAMPLES GDK_PIXBUF; do
    off+=("-DWITH_$w=OFF")
done
cmake -S "$heif" -B "$work/build-heif" "${common[@]}" "${off[@]}" \
    -DWITH_LIBDE265=ON -DWITH_LIBDE265_PLUGIN=OFF -DENABLE_PLUGIN_LOADING=OFF -DBUILD_TESTING=OFF -DBUILD_DOCUMENTATION=OFF \
    -DCMAKE_DISABLE_FIND_PACKAGE_ZLIB=ON -DCMAKE_DISABLE_FIND_PACKAGE_Brotli=ON \
    "-DLIBDE265_INCLUDE_DIR=$prefix/include" "-DLIBDE265_LIBRARY=$prefix/lib/libde265.a"
cmake --build "$work/build-heif" --target install -j "$(nproc)"

cmake -S "$here/heif" -B "$work/build-hfmheif" -DCMAKE_BUILD_TYPE=Release "-DDEPS=$prefix" "-DJDK=$JAVA_HOME"
cmake --build "$work/build-hfmheif" -j "$(nproc)"
strip --strip-unneeded "$work/build-hfmheif/libhfmheif.so"

mkdir -p "$out"
cp "$work/build-hfmheif/libhfmheif.so" "$out/"
echo "libhfmheif.so ($(stat -c %s "$out/libhfmheif.so") bytes) -> $out"
echo "needs:"; objdump -p "$out/libhfmheif.so" | grep -E 'NEEDED|GLIBC_2\.[0-9]+' | sed 's/^/  /' | sort -u
