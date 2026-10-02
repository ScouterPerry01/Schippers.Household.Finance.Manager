// Phone-to-desktop transfer (section 3): the pairing invitation, the encrypted bundle format and
// the phone's client. Plain Kotlin, used by the Android app and the desktop.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:security"))
    api(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
