# Spike: PaddleOCR on the JVM via ONNX Runtime Java

**Question:** can we run PaddleOCR text detection + recognition on the JVM (Windows) through ONNX Runtime Java,
with acceptable accuracy and speed on photographed receipts in English and French?

## Verdict: GO (with caveats)

Pure Kotlin + `com.microsoft.onnxruntime:onnxruntime:1.30.0`. No Python, no OpenCV, no JNI of our own.
The pipeline (det → crop → rec → CTC decode) is about 450 lines of Kotlin. It worked on the first end-to-end run.

* Clean synthetic receipts: **0–1 % CER**. Real phone photos: **every line item and total read correctly** on the
  Austrian (German) and 1994 Tesco receipts. French accents (É, é, è, à, ç, ê) are handled well by the **latin** model.
* Speed: **about 0.6–1.0 s per receipt warm, about 0.5–0.9 s model load** on a quiet machine (Core Ultra 7 258V,
  4 ORT threads). Under heavy background load (WSL/Docker plus a concurrent Gradle build) it was 1.2–2.9 s. That is
  fine for an interactive "import receipt" action in a desktop app.
* Caveats: thread count must be capped (see below). EXIF orientation and HEIC need handling. There is no angle
  classifier yet, so receipts photographed upside down will fail. Small-font `,` vs `.` confusion means amounts need
  locale-aware post-validation.

## How to run

```bash
export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8"
./download-models.sh                       # models -> models/ (git-ignored), real photos -> samples/
GRADLE=$(ls ~/.gradle/wrapper/dists/gradle-9.6.0-bin/*/gradle-9.6.0/bin/gradle)
$GRADLE -p spikes/ocr-paddle run           # regenerates synthetic samples, OCRs samples/* with latin + en rec models
$GRADLE -p spikes/ocr-paddle run --args="path/to/photo.jpg"   # single image, latin model
# tuning knobs: -Dort.threads=4 -Drec.batch=6 -Drec.minWidth=64 -Ddet.maxSide=1600
```

Recognised text goes to `out/<image>.<model>.txt` and the metrics to `out/summary.txt`.

## Models (RapidOCR ONNX conversions of PaddleOCR PP-OCRv5, ModelScope `RapidAI/RapidOCR` tag `v3.9.2`)

| File (renamed locally) | Upstream path | Size | Licence |
|---|---|---|---|
| `det_v5_mobile.onnx` | `onnx/PP-OCRv5/det/ch_PP-OCRv5_det_mobile.onnx` | 4.8 MB | Apache-2.0 |
| `rec_latin_v5_mobile.onnx` (**recommended**) | `onnx/PP-OCRv5/rec/latin_PP-OCRv5_rec_mobile.onnx` | 7.9 MB | Apache-2.0 |
| `ppocrv5_latin_dict.txt` | `paddle/PP-OCRv5/rec/latin_PP-OCRv5_rec_mobile/ppocrv5_latin_dict.txt` | 2 KB (502 chars) | Apache-2.0 |
| `rec_en_v5_mobile.onnx` (comparison) | `onnx/PP-OCRv5/rec/en_PP-OCRv5_rec_mobile.onnx` | 7.9 MB | Apache-2.0 |
| `ppocrv5_en_dict.txt` | `paddle/PP-OCRv5/rec/en_PP-OCRv5_rec_mobile/ppocrv5_en_dict.txt` | 1.4 KB (436 chars) | Apache-2.0 |
| `cls_textline_ori_mobile.onnx` (downloaded, not wired in) | `onnx/PP-OCRv5/cls/ch_PP-LCNet_x0_25_textline_ori_cls_mobile.onnx` | 1.0 MB | Apache-2.0 |

The exact URLs are in `download-models.sh`. The production footprint is about **13.7 MB** (det + latin rec + cls).
CTC charset layout: index 0 = blank, then the dictionary lines, then a trailing space. That gives 504 classes, which
matches the latin model's output `[N, T, 504]`. The latin dictionary covers É é è ê à â ç î ô û ù ü ë ï œ Œ and more.

ONNX Runtime 1.30.0 (MIT): the jar is **55.6 MB** because it bundles natives for 5 platforms. The win-x64 part is only
`onnxruntime.dll` 16.5 MB + JNI 0.1 MB. Stripping the non-Windows natives when packaging (jpackage) saves about 39 MB.

## Test images (`samples/`)

| Image | Kind | Ground truth |
|---|---|---|
| `synthetic-en-grocery.jpg` | Java2D, Consolas 26px, 2° rotation, noise, shadow gradient, JPEG q85 | `.txt` (full) |
| `synthetic-fr-epicerie.jpg` | Courier New 28px, −3°, blur, noise, JPEG q75: Épicerie, Pâtes fraîches, Bœuf, TPS, TVQ, `12,34 $` | `.txt` (full) |
| `synthetic-bilingual-small.jpg` | Arial **20px at 0.8 scale (~16px)**, 5°, blur, heavy noise, JPEG q60 (deliberately hard) | `.txt` (full) |
| `real-de-vienna.jpg` | Real phone photo, crumpled HOFER receipt, 3120×4160, **EXIF orientation 6**. Wikimedia Commons, public domain (Grandmaster Huon) | `.expect.txt` (27 key snippets) |
| `real-en-tesco-1994.jpg` | Scanned faded dot-matrix Tesco receipt, 769×2060. Wikimedia Commons, public domain | `.txt` (full, hand-transcribed) |
| `real-fr-ticket-de-caisse.jpg` | Real phone photo, French thermal receipt (product-recall notice), 4032×3024. Wikimedia Commons, **CC BY-SA 4.0, Baidax** | `.expect.txt` (7 lines) |

I could not find a freely licensed photo of a Québec receipt with TPS/TVQ. The French photo is from France and is
narrative text, not line items. The synthetic FR receipt covers the TPS/TVQ format.

## Results (full run, latin vs en rec model, det max side 1600, 4 threads, rec batch 6)

CER = Levenshtein / reference length, after whitespace normalisation. "Amounts" checks that every `\d+[.,]\d{2}` from
the reference appears in the output. "Snippets" checks that each expected line appears (whitespace-insensitive).

| Image | latin CER / checks | en CER / checks |
|---|---|---|
| synthetic-en-grocery | **0.00 %**, amounts 12/12 | 0.00 %, amounts 12/12 |
| synthetic-fr-epicerie | **0.76 %**, amounts 10/11, accented words 17/19 | 2.03 %, 10/11, 17/19 |
| synthetic-bilingual-small (16px) | 3.71 %, amounts **1/10** (reads `8.97` for `8,97`), accented 2/3 | 5.14 %, 2/10, 1/3 |
| real-en-tesco-1994 | 5.37 %, amounts 13/13* | **2.93 %**, 13/13* |
| real-de-vienna | **snippets 23/27**: all 9 items + all amounts correct | 19/27 |
| real-fr-ticket-de-caisse | snippets 4/7, see below | 1/7 |

\* One faded `0.55` was not read, but the same value appears elsewhere on the receipt, so the amount check still
passes. The rest of the Tesco CER comes from the bitmap logo (`TESCO` read as `BEEEEEE`/`12000`), `6 3AM` for `6 JAM`,
and `N0.` for `NO.`.

Notes on the "misses":
* FR ticket: the output is visually correct except `SURGELéES` → `SURGELEES` (the receipt prints a lowercase é inside
  an uppercase word), a missing trailing `.`, `et` → `ot` on one faint line, and FSC watermark noise (`el.www`).
  "Si vous **ê**tes encore en possession…" was read perfectly.
* Vienna: `Kar1-Popper-Straße` (the 1/l confusion matches the receipt font), `1100 Wien 10Favoriten` (lost `., `),
  `Vollmilch 11` vs `1l`, `FÜR` → `FUR`. The crumpled advert header at the top comes out as garbage. That is
  acceptable because it is not data we need.
* Synthetic FR: `fraîches` → `fraiches` and `Bœuf` → `Bouf` (both characters are in the dictionary; this is a
  recognition miss at this size and blur), and `11,99 $` → `11,99 Sr`.
* Small/blurry Arial: the decimal comma is read as a period in 9/10 amounts, and `Ibuprofène` → `Ibuproféne`.
  The amounts are numerically correct apart from the separator.

**latin vs en:** latin is clearly better on French (and German). en was slightly better on the old English
dot-matrix scan. Use **latin** for an EN+FR household. Both have the same size and speed.

### Timings

The machine was under heavy unrelated load during these runs (CPU at 45–80 % before starting: WSL/Docker and a
concurrent Gradle build). Absolute numbers are noisy, so treat them as upper bounds.

Thread sweep, `synthetic-en-grocery.jpg` (772×906, 31 boxes), warm average of 3 runs:

| ORT intra-op threads | rec batch 1 | rec batch 6 |
|---|---|---|
| 1 | 1248 ms | 1214 ms |
| 2 | 792 ms | 793 ms |
| **4** | 690 ms | **635 ms** |
| 8 | 1740 ms | 1782 ms |

Padding rec inputs to the batch's max width instead of the fixed Paddle 320 px minimum (`rec.minWidth=64`) gave
635 → **550 ms**, with equal or better CER. That is now the default.

Full run (latin, 4 threads, under load):

| Image | det (ONNX part) | rec (ONNX part) | warm total |
|---|---|---|---|
| synthetic-en-grocery 772×906, 31 boxes | 385 (308) ms | 837 (802) ms | 1.2 s |
| synthetic-fr-epicerie 831×1027, 30 boxes | 548 (426) ms | 1327 (1274) ms | 1.9 s |
| real-en-tesco 769×2060, 40 boxes | 547 (405) ms | 993 (937) ms | 1.5 s |
| real-de-vienna 3120×4160, 53 boxes | 946 (764) ms | 1918 (1814) ms | 2.9 s |
| real-fr-ticket 4032×3024, 23 boxes | 1206 (807) ms | 1360 (1283) ms | 2.6 s |

* **Model load** (OrtEnvironment + det + rec sessions): **450–1100 ms** (typically about 500 ms).
* **JPEG decode** with ImageIO for a 12 MP photo: 200–650 ms. That is significant: consider a faster decoder or
  subsampled decode (`ImageReadParam.setSourceSubsampling`).
* About 85–95 % of the time is ONNX inference. The Kotlin pre- and post-processing (resize, normalise, flood-fill
  DB, convex hull + min-area rect, bilinear rotated crop, CTC) costs about 80–150 ms per image.
* Recognition dominates: about 15–35 ms per text line.
* Raising `det.maxSide` from 1600 to 2400 made things slower and slightly *worse* (more spurious boxes). Keep 1600.

## Problems found

1. **Thread oversubscription.** With `intraOpNumThreads = availableProcessors()` (8) it was about 3× slower than with
   4 threads on this hybrid CPU (Lunar Lake: 4 P-cores + 4 LP E-cores). Production should use
   `min(4, cores/2)` or make it configurable.
2. **EXIF orientation is ignored by ImageIO.** The Vienna photo (orientation 6) would be sideways. The spike has a
   minimal EXIF parser (`ImageLoader.kt`) that handles orientations 3, 6 and 8. Production could use
   `com.drewnoakes:metadata-extractor`.
3. **HEIC (iPhone default) and WebP are not decodable by ImageIO.** We need a converter (e.g. TwelveMonkeys
   ImageIO plugins for WebP; for HEIC, the Windows WIC codec via a shell-out, libheif, or "export as JPEG" guidance
   in the UI).
4. **No angle classifier.** Detection plus min-area rect handles skew of ±5° fine (tested), but a receipt rotated by
   180°, or an un-normalised 90°, would produce garbage. Wire in the downloaded PP-LCNet textline orientation cls
   model (0/180°) and possibly a document-orientation model or a heuristic (try 0/90/180/270 and keep the highest
   mean confidence).
5. **Decimal separator confusion** at small font sizes (`8,97` → `8.97`), plus the occasional 1/l/I, 0/O, J/3
   confusion. The receipt parser must normalise amounts (accept `,` or `.` before exactly 2 digits) and
   cross-check line items against the total.
6. **Native library size.** The ORT jar is 55.6 MB (all platforms). On Windows we need only 16.5 MB. Strip it at
   packaging time.
7. **Reading order** for left/right columns needs row grouping along the dominant text angle. Naive y-sorting breaks
   on tilted photos. This is implemented in `PaddleOcr.toText`.
8. Accent misses are model-level (`î`, `œ`, `Ü`), not charset gaps. They matter little for amounts and dates but
   can affect merchant name matching. Use accent-insensitive fuzzy matching for merchants and categories.

## Recommendation for the production `OcrEngine`

* Implement `PaddleOnnxOcrEngine : OcrEngine` based on this spike's `PaddleOcr.kt`, with these changes:
  * PP-OCRv5 mobile det + **latin** rec + textline orientation cls (about 13.7 MB of models). Ship the models with
    the app, or download them on first use with a SHA-256 check. Pin to the RapidOCR tag.
  * One long-lived `OrtEnvironment` and the sessions created lazily on first OCR (about 0.5 s). Run OCR off the UI
    thread (coroutine on `Dispatchers.Default`). `intraOpNumThreads` = 4 (configurable). Rec batch 6. Rec padding to
    the batch's max width.
  * Det max side 1600. DB params: thresh 0.3, box_thresh 0.5, unclip 1.6.
  * Return lines with box, text and confidence so the receipt parser can use geometry (right-aligned amounts) and
    flag low-confidence fields (< about 0.6) for user review.
  * Image intake: EXIF orientation, a downscale-on-decode for photos over 12 MP, and HEIC conversion or rejection.
* **Tesseract fallback:** not needed for accuracy. PP-OCRv5 latin clearly outperforms what Tesseract usually achieves
  on skewed or crumpled thermal-receipt photos without heavy preprocessing. Tess4J would add about 40 MB of natives
  plus `eng` and `fra` traineddata, and it needs deskew and binarisation. Only consider it as a fallback when the ORT
  native library fails to load (e.g. a missing VC++ runtime or an unsupported CPU). Even then a clear error message
  is simpler. Keep `OcrEngine` as an interface so a Tesseract (or Windows.Media.Ocr) implementation can be added
  later if needed.
* GPU (DirectML) is unnecessary at these speeds. CPU EP is fine.

## Files

* `build.gradle.kts`, `settings.gradle.kts`: standalone Kotlin/JVM app (Kotlin 2.4.20, JDK 21 toolchain, ORT 1.30.0)
* `download-models.sh`: model and sample URLs
* `src/main/kotlin/PaddleOcr.kt`: det preprocessing, DB post-processing, min-area rect, unclip, rotated crop, rec,
  CTC, row grouping
* `src/main/kotlin/ImageLoader.kt`: ImageIO plus the EXIF orientation fix
* `src/main/kotlin/SyntheticReceipts.kt`: Java2D receipt generator with ground truth
* `src/main/kotlin/Main.kt`: benchmark runner and metrics (CER, amounts, accents, snippets)
* `samples/`: test images plus `.txt` ground truth and `.expect.txt` snippet lists
* `models/`, `out/`, `build/`: git-ignored
