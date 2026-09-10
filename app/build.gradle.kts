import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.moneyplanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.moneyplanner"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }


    /**
     * Release signing.
     *
     * If a keystore.properties file is present it is used, which is how a real release for
     * Play should be signed; that file is git-ignored so the signing key never enters the
     * repository. Without it the release build falls back to the local Android debug key
     * so that a minified APK can still be installed and tested on a device. A
     * debug-signed APK is fine for sideloading and useless for Play, which is the correct
     * trade-off for a build that would otherwise come out unsigned and uninstallable.
     */
    signingConfigs {
        create("release") {
            val keystoreProperties = rootProject.file("keystore.properties")
            if (keystoreProperties.exists()) {
                val properties = Properties().apply {
                    keystoreProperties.inputStream().use { load(it) }
                }
                storeFile = rootProject.file(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            } else {
                storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    /**
     * Two builds of the same app, differing only in whether they can reach a network.
     *
     * The offline flavour is the original app and keeps the guarantee the README makes:
     * no INTERNET permission in its merged manifest, verifiable by anyone who cares to
     * check. It has no Firebase dependency at all, so nothing can quietly reintroduce
     * one through a transitive.
     *
     * The cloud flavour adds Firebase Auth and Storage so a lost phone is not a lost
     * financial history. It necessarily has INTERNET, and its own privacy story.
     *
     * Nothing outside the two source sets knows which is which: the app talks to a
     * CloudSync interface, and each flavour supplies its own binding.
     */
    flavorDimensions += "connectivity"

    productFlavors {
        create("offline") {
            dimension = "connectivity"
            isDefault = true
            buildConfigField("boolean", "CLOUD_ENABLED", "false")
        }
        create("cloud") {
            dimension = "connectivity"
            // A distinct id so both can sit on one device while this is being tested.
            applicationIdSuffix = ".cloud"
            versionNameSuffix = "-cloud"
            buildConfigField("boolean", "CLOUD_ENABLED", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    /**
     * The exported Room schemas ship inside the instrumentation APK.
     *
     * MigrationTestHelper opens a real database at an old version by reading the schema
     * Room exported for it, and it looks for that JSON in the test APK's own assets. With
     * `room.schemaLocation` alone the files exist on disk but never reach the device, so
     * every migration test fails on a missing file before it can test anything.
     */
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    // Writing scheduled backups into a folder the user picks, with no storage permission.
    implementation(libs.androidx.documentfile)

    // Cloud flavour only, so the offline build cannot pull in Play Services or the
    // INTERNET permission through a transitive dependency.
    "cloudImplementation"(platform(libs.firebase.bom))
    "cloudImplementation"(libs.firebase.auth)
    "cloudImplementation"(libs.firebase.storage)
    "cloudImplementation"(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    // Replays a real v2 database through the migration, which is the only way to know a
    // migration works before it runs on somebody's actual records.
    androidTestImplementation(libs.androidx.room.testing)
}


/**
 * The Google services plugin is applied only when a configuration file is present.
 *
 * Applying it unconditionally would break every build on a machine without
 * google-services.json, including the offline flavour that has nothing to do with
 * Firebase. Applied conditionally, a checkout with no Firebase project still builds and
 * tests cleanly, and the cloud flavour reports itself as unconfigured at runtime rather
 * than crashing.
 *
 * Put the file from the Firebase console at app/src/cloud/google-services.json. It is
 * git-ignored: it is not a secret, but it names your project and does not belong to
 * anyone who clones this.
 */
val googleServicesConfig = listOf(
    file("src/cloud/google-services.json"),
    file("google-services.json")
).firstOrNull { it.exists() }

if (googleServicesConfig != null) {
    apply(plugin = "com.google.gms.google-services")
    logger.lifecycle("Firebase config found at ${googleServicesConfig.path}; cloud flavour is configured.")
} else {
    logger.lifecycle(
        "No google-services.json found. The offline flavour builds normally; " +
            "the cloud flavour will build but report itself as not configured."
    )
}
