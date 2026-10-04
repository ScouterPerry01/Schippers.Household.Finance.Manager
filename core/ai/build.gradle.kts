// Cloud AI reading (section 4.5, AI-01 to AI-07, ADR 0009): document schemas and instructions, the
// provider layer with Claude through Anthropic's Java SDK, checks on every answer, the API key in
// the operating system's secret store, and the usage log. Desktop only.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:ocr"))
    api(libs.kotlinx.serialization.json)
    implementation(libs.anthropic.java)
    implementation(libs.jna)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// A test that hangs (a keyring prompt, a socket) fails after a minute with its name, instead of
// holding a CI runner until GitHub stops it.
tasks.test {
    systemProperty("junit.jupiter.execution.timeout.default", "60s")
}
