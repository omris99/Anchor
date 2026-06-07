plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    // Namespace = "com.anchor.watch" so SOURCE files' `import com.anchor.watch.R` resolve.
    // applicationId stays "com.anchor.anchorwatchapp" to preserve partner's Play Store / FCM identity.
    namespace = "com.anchor.watch"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.anchor.anchorwatchapp"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // Keep BOTH shipped locales. Hebrew strings now live in res/values-he/ (renamed
        // from the legacy values-iw/), so the "he" filter actually matches the folder —
        // previously "he" did not match the "iw" qualifier and Hebrew could be stripped,
        // making a Hebrew selection fall back to English. "en" is the default values/.
        localeFilters += listOf("en", "he", "iw")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Bumped to 17 to match SOURCE (kotlinx-coroutines-test 1.7.3 requires JVM 17 byte-code).
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Both src/main/java/ (partner's existing MainActivity + theme) and src/main/kotlin/
    // (transplanted SOURCE tree under com.anchor.watch.*) are active source roots.
    sourceSets {
        getByName("main").kotlin.srcDir("src/main/kotlin")
        getByName("test").kotlin.srcDirs("tests/unit")
        getByName("androidTest").kotlin.srcDirs("tests/instrumented")
    }

    useLibrary("wear-sdk")

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // --- TARGET originals (partner's MainActivity uses these) ---
    implementation(libs.play.services.wearable)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)

    // --- Wear Compose Material 2 (SOURCE screens; coexists with M3) ---
    implementation(libs.wear.compose.material2)
    implementation(libs.wear.compose.navigation)

    // --- AndroidX core (SOURCE deps) ---
    implementation(libs.fragment.ktx)
    implementation(libs.wear)
    implementation(libs.core.ktx)

    // --- Room (SOURCE persistence) ---
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // --- WorkManager (SOURCE sync workers) ---
    implementation(libs.work.runtime.ktx)

    // --- Coroutines (SOURCE) ---
    implementation(libs.kotlinx.coroutines.android)

    // --- Network (PartnerApiAdapter) ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // --- DataStore (X-Watch-Key persistence) ---
    implementation(libs.datastore.preferences)

    // --- QR code generation (WatchPairingScreen) ---
    implementation(libs.zxing.core)

    // --- Health Services for Wear OS (passive HR + steps) ---
    implementation(libs.health.services.client)
    implementation(libs.concurrent.futures.ktx)

    // --- Firebase (FCM silent push for medication sync) ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    // --- Unit tests (SOURCE) ---
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit.ktx)
    testImplementation(libs.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.ui.test.manifest)

    // --- Instrumented (TARGET originals) ---
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
}
