import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    implementation(project(":core:i18n"))
    implementation(project(":core:calc"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.fastexcel)
    implementation(libs.openpdf)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

compose.desktop {
    application {
        mainClass = "ca.schippers.hfm.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "HouseholdFinanceManager"
            packageVersion = "0.1.0"
            description = "Household Finance Manager"
            vendor = "Schippers"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.sql", "java.prefs", "jdk.unsupported")
            windows {
                menuGroup = "Household Finance Manager"
                // Keep this fixed forever: Windows uses it to recognise upgrades of the same app.
                upgradeUuid = "5f0b9a52-7c1e-4d0e-9a3c-2d1f6c8e4b17"
                perUserInstall = true
            }
            linux {
                packageName = "household-finance-manager"
                // TODO: replace with the dedicated support address (DIST-06) before the first release.
                debMaintainer = "support@example.invalid"
                appCategory = "Office"
            }
        }
    }
}

// Opens a throw-away sample household: ./gradlew :app:desktop:runDemo [-Plang=fr] [-Paccount=Visa Desjardins] [-Psection=CATEGORIES]
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
}
