plugins {
    kotlin("jvm") version "2.4.20"
    application
}

dependencies {
    // Only needed for the optional JNA demos (HWND lookup, DPAPI, NCrypt provider probe).
    // The helper-exe integration itself is plain ProcessBuilder.
    implementation("net.java.dev.jna:jna-platform:5.19.1")
}

kotlin { jvmToolchain(21) }

application {
    mainClass.set("hfm.spike.hello.MainKt")
}

// Path to the helper exe (NativeAOT publish output) for `gradle run`.
tasks.named<JavaExec>("run") {
    val exe = layout.projectDirectory.file("../helper/bin/Release/net10.0-windows10.0.19041.0/win-x64/publish/hfm-hello.exe")
    systemProperty("hfm.hello.exe", exe.asFile.absolutePath)
    standardInput = System.`in`
}
