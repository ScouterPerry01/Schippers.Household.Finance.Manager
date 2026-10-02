plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:security"))
    api(libs.sqldelight.runtime)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

sqldelight {
    databases {
        // Household-wide reference data: users, groups, permissions, categories, payees, settings.
        create("CoreDatabase") {
            packageName.set("ca.schippers.hfm.data.core")
            srcDirs.setFrom("src/main/sqldelight/core")
            schemaOutputDirectory.set(file("src/main/sqldelight/core/schemas"))
            dialect("app.cash.sqldelight:sqlite-3-44-dialect:${libs.versions.sqldelight.get()}")
            verifyMigrations.set(true)
        }
        // One ledger per account group: accounts, transactions, documents. Encrypted with the group's key.
        create("LedgerDatabase") {
            packageName.set("ca.schippers.hfm.data.ledger")
            srcDirs.setFrom("src/main/sqldelight/ledger")
            schemaOutputDirectory.set(file("src/main/sqldelight/ledger/schemas"))
            dialect("app.cash.sqldelight:sqlite-3-44-dialect:${libs.versions.sqldelight.get()}")
            verifyMigrations.set(true)
        }
    }
}
