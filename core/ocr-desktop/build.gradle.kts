// Desktop text recognition (OCR-01, ADR 0004): PaddleOCR PP-OCRv5 models run by ONNX Runtime, PDF
// pages read by PDFBox, and HEIC photos through the decoder the user installs. Android uses ML Kit
// instead; both feed core:ocr's extractors.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:ocr"))
    implementation(libs.onnxruntime)
    implementation(libs.pdfbox)
    implementation(libs.jna)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
