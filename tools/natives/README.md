# Native libraries

## HEIC on Windows (`hfmwic`)

HEIC photos (CAP-03, ADR 0004) are read with a decoder the user installs, since HEVC decoding is
covered by patent licences; RANN's Roost ships no HEVC decoder.

On Windows, `wic/hfm_wic.cpp` is a small JNI library that asks Windows Imaging Component to decode a
picture from memory, turns it upright from its orientation, reduces it and returns its pixels. It
contains no codec and links only Windows libraries: HEIC is read once the user has installed
Microsoft's **HEIF Image Extensions** and **HEVC Video Extensions** from the Microsoft Store. The
built file is `core/ocr-desktop/src/main/resources/hfm/heif/native/Windows/x86_64/hfmwic.dll`
(149 KB, static C runtime, needs only `KERNEL32.dll` and `ole32.dll`).

On Linux nothing is built: the app calls the distribution's `libheif.so.1` through JNA, with the HEVC
plugin the user installs (for example `libheif-plugin-libde265` on Debian and Ubuntu, or
`libheif-freeworld` from RPM Fusion on Fedora).

### Rebuilding

- `powershell -File tools/natives/build-wic.ps1` (Visual Studio with C++, `JAVA_HOME` set), which writes straight into the resources folder above; or
- the *Native libraries* workflow (`gh workflow run natives.yml`), then commit its artifact.

Then run `./gradlew :core:ocr-desktop:test` (`HeifTest`). The committed copy was built on
2026-10-04 with MSVC 19.51: SHA-256 `dbf8e325b8678ee25403f407ca441a395ae5c3524578b12c68ae716c23f42f49`.
