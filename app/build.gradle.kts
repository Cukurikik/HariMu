import java.util.Properties

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
        applicationId = "com.harimu.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Rilis hanya ditandatangani bila keystore sungguhan tersedia; clone bersih tetap bisa
    // membangun debug (Gradle memakai debug keystore bawaan) dan release tanpa tanda tangan.
    // Isi keystore.properties di root (JANGAN di-commit) atau set env HARIMU_KEYSTORE_*.
    val releaseKeystoreProps = Properties().apply {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) propsFile.inputStream().use { load(it) }
    }
    val releaseStorePath = releaseKeystoreProps.getProperty("storeFile")
        ?: System.getenv("HARIMU_KEYSTORE_FILE")

    signingConfigs {
        if (!releaseStorePath.isNullOrBlank()) {
            create("releaseConfig") {
                storeFile = rootProject.file(releaseStorePath)
                storePassword = releaseKeystoreProps.getProperty("storePassword")
                    ?: System.getenv("HARIMU_KEYSTORE_PASSWORD")
                keyAlias = releaseKeystoreProps.getProperty("keyAlias") ?: System.getenv("HARIMU_KEY_ALIAS")
                keyPassword = releaseKeystoreProps.getProperty("keyPassword")
                    ?: System.getenv("HARIMU_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("releaseConfig")
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

ksp {
    // Schema Room diekspor supaya migrasi versi berikutnya bisa ditulis & diuji.
    arg("room.schemaLocation", "$projectDir/schemas")
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
    // belum diverifikasi, bergantung hasil CI
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.paging.compose)
    implementation(libs.pdfbox.android)
    implementation(libs.tesseract4android)
    implementation(libs.androidsvg)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.junrar)
    implementation(libs.zip4j)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
}
