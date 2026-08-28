import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.firebase.appdistribution)
}

// Read signing credentials from a gitignored keystore.properties. Falls
// back to the debug keystore when missing -- so the project still builds
// fresh out of git without setup.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// Read Firebase App Distribution config from a gitignored firebase.properties.
// Plugin is always applied so the appDistributionUpload* tasks exist; the
// upload only succeeds when the properties + auth are in place.
val firebasePropsFile = rootProject.file("firebase.properties")
val firebaseProps = Properties().apply {
    if (firebasePropsFile.exists()) firebasePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.jjwalter.pooltemp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jjwalter.pooltemp"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "1.12.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
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
            // Sign with the release config if it was registered above;
            // otherwise leave unsigned (gradle assemble will warn).
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Each buildType configures Firebase App Distribution
            // separately; release is what we ship to the family.
            firebaseAppDistribution {
                appId = (firebaseProps["appId"] as? String).orEmpty()
                testers = (firebaseProps["testers"] as? String).orEmpty()
                groups = (firebaseProps["groups"] as? String).orEmpty()
                // Auth picked up automatically from FIREBASE_TOKEN env var
                // (see README) or you can point this at a JSON service-
                // account key file in firebase.properties.
                (firebaseProps["serviceCredentialsFile"] as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { serviceCredentialsFile = it }
                releaseNotes = "Pool Temp ${defaultConfig.versionName} " +
                    "(build ${defaultConfig.versionCode})"
            }
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
        buildConfig = true
    }

    sourceSets {
        named("main") {
            java.srcDirs("src/main/kotlin")
        }
        named("test") {
            java.srcDirs("src/test/kotlin")
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation(kotlin("test"))
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Coroutines + serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Storage / background
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
