# ADR 0004: Desktop text recognition

Status: Accepted (Phase 0, 2026-10-01); implemented in Phase 2a (2026-10-02) in `core/ocr-desktop`. Spike details in `spikes/ocr-paddle/README.md`.

## Context

OCR-01 and OCR-04: text recognition runs on the user's own device, in English and French. ML Kit is Android-only, so the desktop needs a separate engine.

## Decision

**Engine and models:**
- PaddleOCR PP-OCRv5 running through ONNX Runtime Java, with all pre- and post-processing in pure Kotlin (no OpenCV, no Python).
- **Models:** mobile text detection, the **latin** recognition model, and the text-line orientation classifier. Together they are about 13.7 MB, Apache-2.0 licensed.
- The latin model was clearly better than the English one on French and German text.

**Measured accuracy:**
- English synthetic receipt: 0 % character errors.
- French synthetic receipt: 0.8 % character errors.
- Real crumpled and faded receipts: every amount read correctly in the readable cases.

**Measured speed:** about 0.6–0.7 s per receipt on a quiet machine, plus about 0.5 s to load the models.

**Production engine (`PaddleOnnxOcrEngine`):**
- Implements the shared `OcrEngine` interface.
- Loads models lazily and runs off the UI thread with at most 4 threads (8 was 3x slower).
- Limits the long side of an image to 1600 px for detection.
- Honours the EXIF orientation of photos and downscales large photos while decoding.
- Decodes HEIC photos with the decoder the user installs (added 2026-10-04, below); rejects WebP.
- Flags lines with confidence under 0.6 for review.

**Tesseract:** not needed for accuracy. It may be added later only as a fallback in case the ONNX native library fails to load.

## Consequences

- **Packaging:** ONNX Runtime's jar bundles 5 platforms (55.6 MB). Packaging must keep only the target platform (about 16.5 MB on Windows).
- **Receipt parsing must allow for:**
  - Small text, where commas and periods get confused.
  - Lost French ligatures such as œ.
  - It should normalise decimal separators and check line items and taxes against the total before suggesting values.

## Implemented in Phase 2a (2026-10-02)

- **Engine:** `PaddleOcrEngine` in `core/ocr-desktop`.
  - Models load once, on first use, from the jar.
  - At most 4 threads.
  - Words are grouped into visual rows along the text's slant.
- **Images:** `ImageLoader` honours EXIF orientation and reduces photos larger than 3,200 px while decoding.
- **PDFs:** `DocumentReader` uses a PDF's text layer when it has one (exact, confidence 1.0). Otherwise it renders up to 5 pages at 200 DPI and recognises them. `PdfPages` combines the pages of a phone capture into one PDF.
- **Models in the repository:** `core/ocr-desktop/src/main/resources/hfm/ocr/models/` holds the detection model, the latin recognition model and its dictionary (12.7 MB, Apache-2.0, see `NOTICE.txt`). Keeping them there means builds never depend on a download site. The orientation classifier is not used.
- **Field extraction:** `FieldExtractor` in `core/ocr` is shared with the phone.
  - It reads merchant, date, total, subtotal, GST/HST/QST/PST, currency, payment method, masked card digits, invoice and account numbers, and the due date, in English and French.
  - It checks the subtotal plus taxes against the total.
  - Each field gets a confidence; fields under 0.85 are marked for review.
- **Packaging (done in Phase 4d, 2026-10-03):** see below.

## Packaging (Phase 4d, 2026-10-03)

- Each package is built on the platform it is for, so a Gradle artifact transform (`KeepHostNatives` in `app/desktop/build.gradle.kts`) keeps only the build machine's native libraries in the desktop app's classpath: ONNX Runtime goes from 55.6 MB to 6.3 MB (Windows x64), and the SQLite driver, which carries twenty platforms, from 16.2 MB to 1.0 MB. The Windows installer went from 165 MB to 101 MB.
- `PackagedSelfCheck` proves an installed package still loads them: started with `JAVA_TOOL_OPTIONS=-Dhfm.selfcheck=<file>`, the app reads a receipt line (a small picture in the app, so no system fonts are needed) with OCR, creates and reopens an encrypted household, writes the result and exits without a window. The release workflow runs it on every package.

## HEIC photos (2026-10-04)

CAP-03 lists HEIC, the format iPhones and many Android phones save photos in. ImageIO cannot read it.

**Decision (owner, 2026-10-04): the user installs the HEVC decoder.** HEVC decoding is covered by patent pools (Access Advance, Via LA), and the licence attaches to the decoder. A first version bundled libheif with libde265; it was replaced the same day, so RANN's Roost ships no HEVC decoder:
- **Windows:** a small JNI helper (`tools/natives/wic/hfm_wic.cpp`, 149 KB, no codec, only Windows libraries) asks Windows Imaging Component to decode. HEIC works once the user has installed Microsoft's HEIF Image Extensions and HEVC Video Extensions from the Microsoft Store (many computers have the HEVC extension from their manufacturer; otherwise Microsoft sells it). Microsoft carries the licence.
- **Linux:** the app calls the distribution's `libheif.so.1` through JNA. HEIC works once the user has installed their distribution's HEIC support with its HEVC plugin (`libheif-plugin-libde265` on Debian and Ubuntu; `libheif-freeworld` from RPM Fusion on Fedora).

**How the app behaves.**
- HEIC counts as available only when a tiny HEIC picture kept in the app (`hfm/heif/probe.heic`, stored 64 x 32 with a quarter turn) decodes upright, so a decoder installed without its HEVC part is caught.
- Files are recognised by an HEVC brand in their `ftyp` box (AVIF is not HEIC). Pictures over 120 megapixels are refused; larger photos are reduced while decoding to the loader's 3,200 pixels; a damaged file is refused, never a crash. The picture is decoded from memory, never from a temporary file.
- A HEIC photo is always accepted into the vault (the original file is kept). Where it can be read, it is read, previewed, saved as `.heic` and put into the inventory and receipts PDFs as JPEG. Where it cannot, the import message, the preview and the receipts PDF say so and how to install the decoder; the photo can be read later, once it is installed.
- The packaged self-check reports the decoder it found, or "not installed", which is not a failure; when there is one, it reads the receipt line from a HEIC picture.

**Checked.** `HeifTest`: detection everywhere; decoding, rotation, reduction, damaged files, OCR and PDFs where a decoder exists; the missing-decoder behaviour where none does. On this project's Windows computer (with Microsoft's extensions) and in CI on Linux (which installs the distribution's packages for the test run only), the decoding tests run; GitHub's Windows runners have no HEVC extension and run the missing-decoder test. The release dry run installs the plugin before checking the installed .deb. The Linux path was also checked on Ubuntu 26.04 with libheif 1.21.2, with and without the plugin.
