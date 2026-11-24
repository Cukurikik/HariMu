plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.harimu.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.harimu.xqmzpk"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    bundle {
        abi { enableSplit = true }
        density { enableSplit = true }
        language { enableSplit = false }
    }

    val taskNames = gradle.startParameter.taskNames
    val isBundleTask = taskNames.any { it.contains("bundle", ignoreCase = true) }
    val isReleaseApkTask = taskNames.any { it.contains("assembleRelease", ignoreCase = true) }
    splits {
        abi {
            isEnable = !isBundleTask && (isReleaseApkTask || project.hasProperty("abiSplits"))
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    sourceSets {
        getByName("test") {
            assets.directories.add("src/androidTest/assets")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                val appUserM2 = file("/home/appuser/.m2/repository")
                if (appUserM2.exists() && System.getProperty("robolectric.dependency.repo.url") == null) {
                    it.systemProperty("robolectric.dependency.repo.url", appUserM2.toURI().toString())
                    it.systemProperty("robolectric.dependency.repo.id", "local-m2")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.ffmpeg.kit.full)
    implementation(libs.mlkit.face.detection)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
}
