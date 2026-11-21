plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.drivelink.receiver"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.drivelink.receiver"
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 3
        versionName = "1.4.1"
    }

    // Exercise the real phone transport without packaging it in the receiver APK.
    sourceSets.getByName("androidTest").java.srcDirs(
        "../../android/app/src/main/java/app/drivelink/cast/core/model",
        "../../android/app/src/main/java/app/drivelink/cast/core/protocol",
        "../../android/app/src/main/java/app/drivelink/cast/core/security"
    )

    signingConfigs {
        // The implicit debug signing config already exists; only its v1/v2 flags are tuned.
        // ("getByName" is not a valid `signingConfigs` member in AGP 8 Kotlin DSL.)
        named("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
        val releaseKeystorePath = System.getenv("DRIVELINK_KEYSTORE_PATH")
        val releaseKeystorePassword = System.getenv("DRIVELINK_KEYSTORE_PASSWORD")
        val releaseKeyAlias = System.getenv("DRIVELINK_KEY_ALIAS")
        val releaseKeyPassword = System.getenv("DRIVELINK_KEY_PASSWORD")
        if (!releaseKeystorePath.isNullOrBlank() &&
            !releaseKeystorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank() &&
            file(releaseKeystorePath).exists()
        ) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("junit:junit:4.13.2")
}
