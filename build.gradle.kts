// Top-level build file. Versions live in gradle/libs.versions.toml.
// AGP 9 has built-in Kotlin (bundles KGP 2.2.10) — no kotlin.android plugin needed.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
}
