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
- Decodes HEIC photos with libheif (added 2026-10-04, below); rejects WebP.
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

CAP-03 lists HEIC, the format iPhones and many Android phones save photos in. ImageIO cannot read it, so until now the desktop asked for JPEG instead. Owner's decision (2026-10-03): decode it with libheif, bundled per platform like ONNX Runtime.

- **Library:** libheif 1.23.5 with libde265 1.1.3 (both LGPL-3.0), built from source archives pinned by SHA-256 (`tools/natives/heif.env`), as static libraries linked with a small JNI wrapper (`tools/natives/heif/hfm_heif.c`) into one file per platform: `hfmheif.dll` for Windows x64 (1.9 MB, static C runtime, needs only `KERNEL32.dll`) and `libhfmheif.so` for Linux x86_64 (4.1 MB, built in the manylinux_2_28 container, needs only glibc 2.25 symbols, the C++ runtime inside). Only the HEVC decoder is built in: no encoders, other codecs or plugin loading. A JNI wrapper was chosen over JNA, which would have added a library and its own native part for two functions.
- **In the jar:** the libraries are kept in `core/ocr-desktop` under `hfm/heif/native/<OS>/<arch>/`, like the OCR models, so builds never depend on a download site. `KeepHostNatives` keeps only the target platform's copy in a package. At first use the library is copied into a new temporary folder that only the user can open, and loaded from there.
- **Decoding:** files are recognised by an HEVC brand in their `ftyp` box (AVIF is not decoded). libheif applies the file's rotation and mirroring; pictures over 120 megapixels are refused, and larger photos are reduced in the native code to the loader's 3,200-pixel limit, so a 48-megapixel photo never becomes a 200 MB array. A damaged file is refused with an error, never a crash.
- **Where HEIC is used:** import (file chooser, drag and drop, the watched folder), OCR, the document preview, saving a copy (`.heic`), and the PDFs built from documents (the home inventory and the medical receipts bundle), which embed HEIC photos as JPEG since PDFBox cannot read HEIC. The original file is what the vault keeps.
- **Platforms without a library** (Linux on ARM): HEIC files are refused as unreadable, as before.
- **Checked:** `HeifTest` (brand detection, rotation, reduction, damaged files, OCR from a HEIC photo, PDFs) runs in CI on Windows and Linux; the packaged self-check decodes `selfcheck.heic` and reads its text, on every package the release workflow builds. The test pictures were made with libheif's own encoder (x265).
- **Rebuilding:** `tools/natives/README.md`, or the *Native libraries* workflow, run by hand.
- **To keep in mind:** HEVC is covered by patent pools (Access Advance, Via LA). Free and open-source projects commonly ship libde265 (GIMP, ImageMagick, Krita), but Fedora leaves it out for this reason and Microsoft sells its HEVC extension. Whether a paid app needs a licence is a question for the owner, not a technical one.
