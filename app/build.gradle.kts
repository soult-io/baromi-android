plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
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
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")

    // Room — the frozen data layer (Module A). Room 2.7+ is required for KSP2 (Kotlin 2.2).
    implementation("androidx.room:room-runtime:2.7.3")
    implementation("androidx.room:room-ktx:2.7.3")
    ksp("androidx.room:room-compiler:2.7.3")

    // Local unit tests run Room in-memory under Robolectric (no emulator needed).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.room:room-testing:2.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
