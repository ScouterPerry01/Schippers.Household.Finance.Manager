# ADR 0004: Desktop text recognition

Status: Accepted (Phase 0, 2026-10-01). Details in `spikes/ocr-paddle/README.md`.

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
- Converts or rejects HEIC/WebP images.
- Flags lines with confidence under 0.6 for review.

**Tesseract:** not needed for accuracy. It may be added later only as a fallback in case the ONNX native library fails to load.

## Consequences

- **Packaging:** ONNX Runtime's jar bundles 5 platforms (55.6 MB). Packaging must keep only the target platform (about 16.5 MB on Windows).
- **Receipt parsing must allow for:**
  - Small text, where commas and periods get confused.
  - Lost French ligatures such as œ.
  - It should normalise decimal separators and check line items and taxes against the total before suggesting values.
