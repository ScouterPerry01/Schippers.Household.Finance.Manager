# Native libraries

## HEIC decoder (`hfmheif`)

HEIC photos on the desktop (CAP-03, ADR 0004) are decoded by [libheif](https://github.com/strukturag/libheif)
with [libde265](https://github.com/strukturag/libde265), both LGPL-3.0. They are built as static
libraries and linked with `heif/hfm_heif.c`, a small JNI wrapper, into one library per platform that
needs nothing beyond the operating system's C library:

| Platform | File in `core/ocr-desktop/src/main/resources/hfm/heif/native/` |
|---|---|
| Windows x64 | `Windows/x86_64/hfmheif.dll` (static C runtime) |
| Linux x86_64 | `Linux/x86_64/libhfmheif.so` (glibc 2.28 or later; the C++ runtime is inside) |

Only the HEVC decoder is built in: no encoders, no other codecs, no plugin loading. The versions and
the SHA-256 of their source archives are pinned in `heif.env`; the scripts refuse a download that
does not match.

The app copies the library for its platform into a new private temporary folder and loads it from
there (`Heif.kt`). Packaging keeps only the target platform's copy (`KeepHostNatives` in
`app/desktop/build.gradle.kts`), and the packaged self-check decodes a HEIC picture.

### Rebuilding

- **Windows:** `powershell -File tools/natives/build-heif.ps1` (Visual Studio with C++, `JAVA_HOME` set).
- **Linux:** in the manylinux_2_28 container, as the comment at the top of `build-heif.sh` shows.
- **Both, on GitHub:** run the *Native libraries* workflow (`gh workflow run natives.yml`) and commit the two artifacts.

Each script writes straight into the resources folder above. Run `./gradlew :core:ocr-desktop:test`
afterwards (`HeifTest`).

The committed copies were built on 2026-10-04 from libheif 1.23.5 and libde265 1.1.3, the DLL with
MSVC 19.51 and the Linux library in `quay.io/pypa/manylinux_2_28_x86_64` (GCC 14):

- `hfmheif.dll`: SHA-256 `76835311d6644bb9332b9892b55ddfd24452f8dbad1256b34762a39b91c36096`
- `libhfmheif.so`: SHA-256 `d960d76d09931ae48429037dfe2016b36a007d625fe85db8123556d18a7dd9c1`

### Licence

libheif and libde265 are LGPL-3.0. Since they are linked statically, the LGPL asks that users can
rebuild the app with a changed version: the application's source, `hfm_heif.c` and these scripts
are all in this repository, so anyone can. The desktop's third-party notices name them.
