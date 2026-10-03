// Release signing (SEC-08, ADR 0008): makes RANN's key pair, then signs each release's files and
// writes its SHA256SUMS and signed update.json. Run by the release workflow; not part of the apps.
//   ./gradlew :tools:release:run --args="keygen <secret key file>"
//   ./gradlew :tools:release:installDist, then with HFM_RELEASE_KEY set:
//   tools/release/build/install/release/bin/release sign-release <version> <folder> [notes-en.md] [notes-fr.md]
//   (run directly, so the secret never passes through Gradle or its configuration cache)
//   ./gradlew :tools:release:run --args="verify <file>"
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":core:update"))
}

application {
    mainClass.set("ca.schippers.hfm.release.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
