// Desktop (JVM) implementation of encrypted database access, using SQLite3 Multiple Ciphers
// in SQLCipher v4 mode. Android uses net.zetetic:sqlcipher-android instead.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:data"))
    implementation(libs.sqldelight.jdbc.driver) {
        // Replaced by the encryption-capable fork, which uses the same org.sqlite package.
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }
    implementation(libs.sqlite.jdbc.crypt)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
