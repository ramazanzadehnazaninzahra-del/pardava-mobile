import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase (optional): drop a real google-services.json into app/ to enable the
// google-services plugin and Google Sign-In. OTP login works without it.
val googleServicesFile = file("google-services.json")

// Release signing (optional): local keystore.properties (gitignored) wires the
// release build to a real key. Without it the release APK stays unsigned.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "ir.pardava.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.pardava.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "0.9.1"
        vectorDrawables { useSupportLibrary = true }
        // Base URL is the SITE ROOT; the app appends api/courses/… itself.
        // Override with: ./gradlew assembleDebug -PpardavaBaseUrl=https://staging.example.com/
        buildConfigField("String", "DEFAULT_BASE_URL", "\"${project.findProperty("pardavaBaseUrl") ?: "https://pardava.ir/"}\"")
        // Web client id for Google Sign-In (empty until google-services.json is provisioned;
        // the server endpoint POST api/courses/auth/google is already wired).
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${project.findProperty("pardavaGoogleClientId") ?: ""}\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// Apply the google-services plugin only when the config file exists, so the
// project builds out of the box before Firebase is provisioned (stage 4b).
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.appcompat)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)

    // Media3/ExoPlayer — professional video playback with resume
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
