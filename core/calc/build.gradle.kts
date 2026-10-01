plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:money"))
    api(libs.kotlinx.datetime)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
