import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
val hasKeystoreProperties = keystorePropertiesFile.exists()

if (hasKeystoreProperties) {
  keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val hasReleaseKeystore = hasKeystoreProperties &&
  listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all {
    !keystoreProperties.getProperty(it).isNullOrBlank()
  }

// Pick the game with -Pgame=daytona2 | scudplusa | srally2 (default srally2).
val gameId = (project.findProperty("game") as String?) ?: "srally2"
val gameTable = mapOf(
  // id        to (title,            ROM zips needed, parent first is fine)
  "srally2"   to Pair("Sega Rally 2",   "srally2"),
  "daytona2"  to Pair("Daytona USA 2",  "daytona2"),
  "scudplusa" to Pair("Scud Race Plus", "scudplusa,scud"),
)
val gameInfo = gameTable[gameId] ?: error("Unknown game '$gameId'. Use: ${gameTable.keys}")

android {
  namespace = "com.izzy2lost.super3"
  compileSdk = 36
  buildToolsVersion = "36.1.0"
  ndkVersion = "29.0.14206865"

  defaultConfig {
    applicationId = "com.treegecko.$gameId"
    manifestPlaceholders["appLabel"] = gameInfo.first
    buildConfigField("String", "GAME_NAME", "\"$gameId\"")
    buildConfigField("String", "GAME_TITLE", "\"${gameInfo.first}\"")
    buildConfigField("String", "ROM_FILES", "\"${gameInfo.second}\"")
    minSdk = 26
    targetSdk = 36

    versionCode = 23
    versionName = "1.2.2"

    ndk {
      abiFilters += listOf("arm64-v8a")
    }

    externalNativeBuild {
      cmake {
        cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
      }
    }
  }

  // Per-game launcher icons live in src/icons/<game>/res (release overrides main).
  sourceSets.getByName("release").res.srcDir("src/icons/$gameId/res")

  buildFeatures {
    buildConfig = true
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
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      if (hasReleaseKeystore) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.30.3"
    }
  }

  packaging {
    resources.excludes += setOf(
      "**/*.md",
      "META-INF/LICENSE*",
      "META-INF/NOTICE*"
    )
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("androidx.documentfile:documentfile:1.0.1")
  implementation("androidx.recyclerview:recyclerview:1.4.0")
  implementation("com.google.android.material:material:1.14.0-alpha07")
}
