import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Bump with versionName for every published version; each flavor adds its own last digit.
val release = 1

// Without keystore.properties, release builds fall back to the debug key.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    enableKotlin = false
    namespace = "dev.kadirozturk.watchface.compiletime"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.kadirozturk.watchface.compiletime"
        targetSdk = 36
        versionName = "1.0.0"
    }

    // A watch rejects a face declaring a newer format than its runtime, so each generation ships separately;
    // the higher versionCode wins on watches that can run both.
    flavorDimensions += "wearOs"
    productFlavors {
        create("wearOs6") {
            dimension = "wearOs"
            minSdk = 36
            versionCode = release * 10 + 6
            manifestPlaceholders["wffVersion"] = "4"
        }
        create("wearOs4") {
            dimension = "wearOs"
            minSdk = 33
            versionCode = release * 10 + 4
            manifestPlaceholders["wffVersion"] = "1"
        }
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    // R8 strips the generated R classes, so the APK ships without a dex file.
    buildTypes {
        release {
            isMinifyEnabled = true
            // Resources referenced only from watchface.xml look unused to the shrinker.
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
}

androidComponents {
    onVariants { variant ->
        val generate = tasks.register<GenerateWatchFace>("generate${variant.name.replaceFirstChar(Char::uppercase)}WatchFace") {
            formatVersion = variant.manifestPlaceholders.getting("wffVersion").map(String::toInt)
        }
        variant.sources.res?.addGeneratedSourceDirectory(generate, GenerateWatchFace::outputDir)
    }
}
