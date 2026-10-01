# ADR 0001: Technology stack and module structure

Status: Accepted (Phase 0, 2026-10-01)

## Context

SRS §2.2 recommends Kotlin Multiplatform with Compose, SQLite with SQLCipher, and one shared calculation library (ARC-03), for Windows, Linux and Android. Apple platforms are out of scope.

## Decision

- **Kotlin everywhere; Compose for UI.** Compose Multiplatform (Desktop) on the JVM for Windows and Linux; Jetpack Compose for Android.
- **Shared code is plain Kotlin/JVM libraries, not Kotlin Multiplatform modules.** Every target runs on the JVM (desktop on JDK 21, Android on ART), so ordinary JVM libraries compiled to Java 17 bytecode are consumed by both apps unchanged. This keeps the build simple and gives the shared code `BigDecimal`, `java.time`, `java.text` locale data and the JCA cryptography on both platforms. A Kotlin Multiplatform module can still be added later if desktop and phone ever need to share UI.
- **Money:** `Money` holds a `Long` count of minor units plus a `Currency` (ISO 4217 minor units, 8 decimals for crypto), with `BigDecimal` used only for rates and conversions. Default rounding is half-up.
- **Database:** SQLite through SQLDelight (type-safe SQL, migrations verified at build time). Desktop: SQLite3 Multiple Ciphers JDBC (`io.github.willena:sqlite-jdbc`) in SQLCipher v4 mode. Android: `net.zetetic:sqlcipher-android` when the phone needs a local database.
- **Cryptography:** Bouncy Castle (Argon2id, X25519, HKDF) and the JCA (AES-256-GCM). It is pure Java, so it behaves identically on desktop and Android.
- **Build:** Gradle with a version catalog; AGP 9 for Android; GitHub Actions on Windows and Ubuntu.

## Consequences

- The Android app and the desktop always produce identical figures from the same compiled code.
- Generated SQLDelight code is JVM code, and each app supplies its own driver.
- Versions at the time of writing: Kotlin 2.4.20, Compose Multiplatform 1.12.1, AGP 9.4.1, Gradle 9.6.0, SQLDelight 2.4.0.
