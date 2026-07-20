// Top-level build file. Plugin versions declared once here, applied in :app.
// AGP 9 has built-in Kotlin (bundles KGP 2.2.10) — no kotlin.android plugin needed.
// KSP is pinned to the matching floor; AGP upgrades it to track the bundled KGP if needed.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
}
