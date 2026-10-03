// Update checks (SEC-08, DIST-05, ADR 0008): the signed release manifest, version comparison and
// download checks. Plain Kotlin, shared by the desktop (Linux packages) and the phone (GitHub build).
// Each app fetches the files itself; nothing here touches the network.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:security"))
    api(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
