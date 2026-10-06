import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// The desktop app runs on its own bundled JDK 21 runtime.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}

dependencies {
    implementation(project(":core:data-jdbc"))
    implementation(project(":core:books"))
    implementation(project(":core:ocr-desktop"))
    implementation(project(":core:ai"))
    implementation(project(":core:i18n"))
    implementation(project(":core:calc"))
    implementation(project(":core:update"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.fastexcel)
    implementation(libs.openpdf)
    implementation(libs.zxing.core)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

val appVersion = providers.gradleProperty("hfm.version").get()

// The app reads its own version (About, update check) from hfm/version.properties.
val versionResource = tasks.register<WriteProperties>("versionResource") {
    destinationFile.set(layout.buildDirectory.file("generated/version/hfm/version.properties"))
    property("version", appVersion)
}
sourceSets.main { resources.srcDir(versionResource.map { it.destinationFile.get().asFile.parentFile.parentFile }) }

// Packages are built on the platform they are for, so the desktop app keeps only the build
// machine's native libraries from ONNX Runtime (55.6 MB with five platforms) and the SQLite driver
// (twenty platforms). ADR 0004. PackagedSelfCheck proves the installed app still loads them.
abstract class KeepHostNatives : TransformAction<KeepHostNatives.Parameters> {
    interface Parameters : TransformParameters {
        /** Folders of native libraries inside jars, e.g. "ai/onnxruntime/native/". */
        @get:Input val nativeRoots: ListProperty<String>
        /** The folders to keep, e.g. "ai/onnxruntime/native/win-x64/". */
        @get:Input val keep: ListProperty<String>
        /**
         * Native files mixed with classes, as "root=name part": under the root, only files whose
         * name contains the part are dropped (JNA keeps "com/sun/jna/<platform>/jnidispatch" beside its classes).
         */
        @get:Input val nativeFiles: ListProperty<String>
    }

    @get:InputArtifact abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val jar = input.get().asFile
        val roots = parameters.nativeRoots.get()
        val keep = parameters.keep.get()
        val files = parameters.nativeFiles.get().map { it.substringBefore('=') to it.substringAfter('=') }
        fun kept(name: String) = keep.any { name.startsWith(it) || it.startsWith(name) }
        fun dropped(name: String) =
            (roots.any { name.startsWith(it) && name != it } || files.any { (root, part) -> name.startsWith(root) && part in name.substringAfterLast('/') }) && !kept(name)
        val hasNatives = ZipFile(jar).use { zip -> zip.entries().asSequence().any { dropped(it.name) } }
        if (!hasNatives) {
            outputs.file(input)
            return
        }
        val out = outputs.file(jar.nameWithoutExtension + "-host.jar")
        ZipFile(jar).use { zip ->
            ZipOutputStream(out.outputStream()).use { zos ->
                for (entry in zip.entries()) {
                    if (dropped(entry.name)) continue
                    zos.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    zip.getInputStream(entry).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
}

val hostNatives = Attribute.of("ca.schippers.hfm.hostNatives", Boolean::class.javaObjectType)
val hostOs = System.getProperty("os.name").lowercase().let {
    when {
        it.startsWith("windows") -> "Windows"
        it.startsWith("mac") -> "Mac"
        else -> "Linux"
    }
}
val hostArch = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "aarch64" else "x86_64"

dependencies {
    attributesSchema { attribute(hostNatives) }
    artifactTypes.getByName("jar") { attributes.attribute(hostNatives, false) }
    registerTransform(KeepHostNatives::class) {
        from.attribute(hostNatives, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(hostNatives, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        parameters {
            nativeRoots.set(listOf("ai/onnxruntime/native/", "org/sqlite/native/", "hfm/heif/native/"))
            nativeFiles.set(listOf("com/sun/jna/=jnidispatch"))
            val onnx = mapOf("Windows" to "win", "Mac" to "osx", "Linux" to "linux").getValue(hostOs) + "-" +
                (if (hostArch == "aarch64") "aarch64" else "x64")
            val jna = mapOf("Windows" to "win32", "Mac" to "darwin", "Linux" to "linux").getValue(hostOs) + "-" + (if (hostArch == "aarch64") "aarch64" else "x86-64")
            keep.set(listOf("ai/onnxruntime/native/$onnx/", "org/sqlite/native/$hostOs/$hostArch/", "hfm/heif/native/$hostOs/$hostArch/", "com/sun/jna/$jna/"))
        }
    }
}

configurations.runtimeClasspath { attributes.attribute(hostNatives, true) }

compose.desktop {
    application {
        mainClass = "ca.schippers.hfm.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            // jpackage's Linux install scripts put the name in single quotes, which a plain apostrophe
            // breaks; Linux packages use the typographic one (U+2019), which reads the same in menus.
            packageName = if (hostOs == "Linux") "RANN\u2019s Roost" else "RANN's Roost"
            packageVersion = appVersion
            description = "RANN's Roost: household finances for Canada"
            vendor = "RANN"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.sql", "java.prefs", "jdk.unsupported")
            windows {
                menuGroup = "RANN's Roost"
                // Keep this fixed forever: Windows uses it to recognise upgrades of the same app.
                upgradeUuid = "5f0b9a52-7c1e-4d0e-9a3c-2d1f6c8e4b17"
                perUserInstall = true
                iconFile.set(rootProject.file("branding/desktop/ranns-roost.ico"))
            }
            linux {
                packageName = "ranns-roost"
                // Dedicated support address (DIST-06).
                debMaintainer = "RANN <info-rann-apps@NorthMail.ca>"
                appCategory = "Office"
                iconFile.set(rootProject.file("branding/desktop/ranns-roost.png"))
            }
        }
    }
}

// Opens a throw-away sample household (an Ontario family in English, a Quebec family in French):
// ./gradlew :app:desktop:runDemo [-Plang=fr] [-Paccount="TD Visa"] [-Psection=CATEGORIES]
tasks.register<JavaExec>("runDemo") {
    group = "application"
    description = "Runs the desktop app with a temporary sample household"
    mainClass.set("ca.schippers.hfm.desktop.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("hfm.demo", "true")
    providers.gradleProperty("lang").orNull?.let { systemProperty("hfm.demo.lang", it) }
    providers.gradleProperty("account").orNull?.let { systemProperty("hfm.demo.account", it) }
    providers.gradleProperty("section").orNull?.let { systemProperty("hfm.demo.section", it) }
    providers.gradleProperty("reconcile").orNull?.let { systemProperty("hfm.demo.reconcile", it) }
    providers.gradleProperty("view").orNull?.let { systemProperty("hfm.demo.report", it) }
    providers.gradleProperty("search").orNull?.let { systemProperty("hfm.demo.search", it) }
    providers.gradleProperty("manual").orNull?.let { systemProperty("hfm.demo.manual", it) }
    // A test release folder (update.json, its .minisig, release-key.pub, the files) for the update screens.
    providers.gradleProperty("update").orNull?.let { systemProperty("hfm.demo.update", it) }
    // A local stand-in for the AI service, for trying AI reading without a key or cost.
    providers.gradleProperty("aiUrl").orNull?.let { systemProperty("hfm.demo.aiUrl", it) }
}

// The manual's pictures (docs/manual-format.md): the sample household's screens drawn offscreen,
// with no window and nothing else from the desktop, at a fixed size in light colours:
// ./gradlew :app:desktop:manualScreenshots -Plang=en|fr [-Pshot=accounts]
// A source set of its own, so the picture taker and the test library never ship with the app.
val screenshots: SourceSet = sourceSets.create("screenshots")
kotlin.target.compilations.getByName("screenshots").associateWith(kotlin.target.compilations.getByName("main"))
configurations.getByName("screenshotsImplementation").extendsFrom(configurations.implementation.get())
configurations.getByName("screenshotsRuntimeClasspath").attributes.attribute(hostNatives, true)
dependencies {
    "screenshotsImplementation"(libs.compose.ui.test)
}
// Compiled with every build, so a change to a screen that breaks the picture taker shows at once.
tasks.named("check") { dependsOn(tasks.named("screenshotsClasses")) }

tasks.register<JavaExec>("manualScreenshots") {
    group = "documentation"
    description = "Draws the manual's pictures of the sample household (-Plang=en or fr)"
    mainClass.set("ca.schippers.hfm.desktop.shots.ManualScreenshotsKt")
    classpath = screenshots.runtimeClasspath
    val lang = providers.gradleProperty("lang").getOrElse("en")
    systemProperty("hfm.shots.lang", lang)
    systemProperty("hfm.shots.out", rootProject.file("core/i18n/src/main/resources/hfm/manual/$lang/images").absolutePath)
    providers.gradleProperty("shot").orNull?.let { systemProperty("hfm.shots.only", it) }
    // Skia draws offscreen without a window; no AWT window is ever opened.
    systemProperty("java.awt.headless", "true")
    // All the pictures are taken in one run of the test library, longer than its one-minute default.
    systemProperty("kotlinx.coroutines.test.default_timeout", "30m")
    // The AI screen shows where added document types go; a made-up user's folder, not this one's.
    if (hostOs == "Windows") environment("APPDATA", "C:\\Users\\Alex\\AppData\\Roaming")
}

// The computer side for the manual's phone pictures: the sample household listening for a phone,
// with no window. It writes the pairing text to build/phone-invitation.txt and stops when
// build/phone-stop appears: ./gradlew :app:desktop:manualPhoneHost -Plang=en|fr (tools/dev/README.md).
tasks.register<JavaExec>("manualPhoneHost") {
    group = "documentation"
    description = "Runs the sample household for pairing the emulator, without a window (-Plang=en or fr)"
    mainClass.set("ca.schippers.hfm.desktop.shots.PhoneHostKt")
    classpath = screenshots.runtimeClasspath
    systemProperty("hfm.shots.lang", providers.gradleProperty("lang").getOrElse("en"))
    systemProperty("hfm.phone.invitation", layout.buildDirectory.file("phone-invitation.txt").get().asFile.absolutePath)
    systemProperty("hfm.phone.stop", layout.buildDirectory.file("phone-stop").get().asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
}

// Microsoft Store package (DIST-01): the same app image as the MSI, with the Store identity, the
// tiles from branding/msix and a resource index so Windows picks each tile's size. Unsigned: the
// Store signs it on submission. Windows only (needs makeappx and makepri from the Windows SDK).
abstract class PackageMsix @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory abstract val appImage: DirectoryProperty
    @get:InputDirectory abstract val assets: DirectoryProperty
    @get:InputFile abstract val manifestTemplate: RegularFileProperty
    @get:InputFile abstract val identity: RegularFileProperty
    @get:Input abstract val version: Property<String>
    @get:Input abstract val executable: Property<String>
    @get:OutputFile abstract val msix: RegularFileProperty
    @get:Internal abstract val workDir: DirectoryProperty

    @TaskAction
    fun pack() {
        val sdk = File("C:/Program Files (x86)/Windows Kits/10/bin").listFiles().orEmpty()
            .filter { File(it, "x64/makeappx.exe").isFile }
            .maxByOrNull { it.name.split('.').map(String::toInt).fold(0L) { acc, n -> acc * 100000 + n } }
            ?.resolve("x64") ?: throw GradleException("makeappx.exe not found: install the Windows SDK")
        val props = Properties().apply { identity.get().asFile.reader(Charsets.UTF_8).use { load(it) } }
        fun xml(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        val work = workDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val layout = File(work, "layout")
        appImage.get().asFile.copyRecursively(layout)
        assets.get().asFile.copyRecursively(File(layout, "Assets"))
        File(layout, "AppxManifest.xml").writeText(
            manifestTemplate.get().asFile.readText()
                .replace("@IDENTITY_NAME@", xml(props.getProperty("identity.name")))
                .replace("@IDENTITY_PUBLISHER@", xml(props.getProperty("identity.publisher")))
                .replace("@PUBLISHER_DISPLAY_NAME@", xml(props.getProperty("publisher.displayName")))
                .replace("@DISPLAY_NAME@", xml(props.getProperty("display.name")))
                .replace("@EXECUTABLE@", xml(executable.get()))
                .replace("@VERSION@", version.get()),
            Charsets.UTF_8,
        )
        val config = File(work, "priconfig.xml")
        exec.exec { commandLine(File(sdk, "makepri.exe"), "createconfig", "/cf", config, "/dq", "en-CA_fr-CA", "/o") }
        exec.exec { commandLine(File(sdk, "makepri.exe"), "new", "/pr", layout, "/cf", config, "/of", File(layout, "resources.pri"), "/o") }
        val out = msix.get().asFile.apply { parentFile.mkdirs() }
        exec.exec { commandLine(File(sdk, "makeappx.exe"), "pack", "/d", layout, "/p", out, "/o") }
    }
}

tasks.register<PackageMsix>("packageMsix") {
    group = "compose desktop"
    description = "Builds the Microsoft Store package (MSIX)"
    val distributable = tasks.named("createDistributable")
    dependsOn(distributable)
    appImage.set(layout.buildDirectory.dir("compose/binaries/main/app/RANN's Roost"))
    executable.set("RANN's Roost.exe")
    assets.set(rootProject.layout.projectDirectory.dir("branding/msix/Assets"))
    manifestTemplate.set(layout.projectDirectory.file("packaging/msix/AppxManifest.xml"))
    identity.set(layout.projectDirectory.file("packaging/msix/store-identity.properties"))
    version.set(appVersion)
    msix.set(layout.buildDirectory.file("compose/binaries/main/msix/RANNsRoost-$appVersion-x64.msix"))
    workDir.set(layout.buildDirectory.dir("msix"))
}
