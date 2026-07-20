// Top-level build file. Plugin versions declared once here, applied in :app.
// Toolchain mirrors the FRAME-DESK build host (Embara AGP/Gradle/SDK + queens-escape Kotlin).
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.2" apply false
}
