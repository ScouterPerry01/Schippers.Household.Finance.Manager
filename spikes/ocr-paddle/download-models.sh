#!/usr/bin/env bash
# Downloads PaddleOCR PP-OCRv5 ONNX models (RapidOCR conversions) into ./models.
# Source: ModelScope RapidAI/RapidOCR, pinned to tag v3.9.2. Licence: Apache-2.0 (PaddleOCR + RapidOCR).
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p models
BASE="https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.9.2"
fetch() { echo "-> $2"; curl -fL --retry 3 -o "models/$2" "$BASE/$1"; }
# Detection (multilingual DB, PP-OCRv5 mobile)
fetch onnx/PP-OCRv5/det/ch_PP-OCRv5_det_mobile.onnx            det_v5_mobile.onnx
# Recognition: latin (en+fr+de+es...; includes accented chars) and English-only for comparison
fetch onnx/PP-OCRv5/rec/latin_PP-OCRv5_rec_mobile.onnx         rec_latin_v5_mobile.onnx
fetch paddle/PP-OCRv5/rec/latin_PP-OCRv5_rec_mobile/ppocrv5_latin_dict.txt  ppocrv5_latin_dict.txt
fetch onnx/PP-OCRv5/rec/en_PP-OCRv5_rec_mobile.onnx            rec_en_v5_mobile.onnx
fetch paddle/PP-OCRv5/rec/en_PP-OCRv5_rec_mobile/ppocrv5_en_dict.txt        ppocrv5_en_dict.txt
# Optional: text line orientation classifier (0/180 deg)
fetch onnx/PP-OCRv5/cls/ch_PP-LCNet_x0_25_textline_ori_cls_mobile.onnx  cls_textline_ori_mobile.onnx
ls -l models

# ---------------------------------------------------------------------------------------------
# Real receipt photos (Wikimedia Commons). Kept separate from models; re-run to refresh samples/.
#  - real-fr-ticket-de-caisse.jpg : "2023-02-19 23-37-01 - Ticket de caisse rappel de produit.jpg", CC BY-SA 4.0
#  - real-de-vienna.jpg           : "Grocery Store Receipt in Vienna.jpg", Public domain
#  - real-en-tesco-1994.jpg       : "Tesco grocery receipt Finchley 1994.jpg", Public domain
UA="ocr-paddle-spike/0.1"
sample() { echo "-> samples/$2"; curl -fL --retry 3 -A "$UA" -o "samples/$2" "$1"; }
mkdir -p samples
sample "https://upload.wikimedia.org/wikipedia/commons/3/31/2023-02-19_23-37-01_-_Ticket_de_caisse_rappel_de_produit.jpg" real-fr-ticket-de-caisse.jpg
sample "https://upload.wikimedia.org/wikipedia/commons/b/bc/Grocery_Store_Receipt_in_Vienna.jpg" real-de-vienna.jpg
sample "https://upload.wikimedia.org/wikipedia/commons/4/45/Tesco_grocery_receipt_Finchley_1994.jpg" real-en-tesco-1994.jpg
