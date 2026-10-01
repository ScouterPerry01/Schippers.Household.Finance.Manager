plugins {
    kotlin("jvm") version "2.4.20"
    application
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime:1.30.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("spike.ocr.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx2g", "-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8")
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir
    // forward tuning knobs: gradle run -Dort.threads=4 -Drec.batch=6 -Ddet.maxSide=1600
    listOf("ort.threads", "rec.batch", "rec.minWidth", "det.maxSide").forEach { k -> System.getProperty(k)?.let { systemProperty(k, it) } }
}
