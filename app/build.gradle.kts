plugins {
    // AGP 9 compiles Kotlin natively; only KSP is applied on top for Room's compiler.
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.soult.baromi"

    // AGP 9 DSL. SDK 37 is the platform installed on the FRAME-DESK build host.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.soult.baromi"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing is env-driven so no keystore or password ever lives in the repo. The config is
    // created only when BAROMI_KEYSTORE_FILE is set (CI's release job / a local signed build); without
    // it, release builds are produced unsigned and CI signs the artifacts separately. App-specific env
    // names keep this distinct from the sibling Android apps on the shared FRAME-DESK-org runner.
    signingConfigs {
        val keystoreFile = System.getenv("BAROMI_KEYSTORE_FILE")
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("BAROMI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BAROMI_KEY_ALIAS")
                keyPassword = System.getenv("BAROMI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (System.getenv("BAROMI_KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Keep AGP's encrypted dependency report out of the APK signing block. F-Droid builds Baromi from
    // source and flags that block; Google Play still gets the report through the app bundle.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        managedDevices {
            localDevices {
                create("aospAtd") {
                    device = "Pixel 2"
                    apiLevel = 35
                    systemImageSource = "aosp_atd"
                }
            }
        }
    }
}

// Built-in Kotlin config (AGP 9): keep the Kotlin JVM target aligned with Java above.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Room exports its generated schema (one JSON per version) here so the on-disk shape is committed
// and reviewable, and future migrations can be validated against the baseline. app/schemas/ is
// tracked in git, not generated output to ignore.
ksp {
    // $projectDir is the :app module dir, so this resolves to app/schemas/.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Room — the frozen data layer (Module A). Room 2.7+ is required for KSP2 (Kotlin 2.2).
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Local unit tests run Room in-memory under Robolectric (no emulator needed).
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
}
