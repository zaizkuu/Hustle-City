import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The permanent release key: from the environment (CI fills it in from the repository's secrets) or from
// keystore.properties next to settings.gradle.kts (storeFile, storePassword, keyAlias; never committed). Every update
// must be signed with the same key, or Android won't install it over the old one. Without a key, release builds fall
// back to the debug key so they still install for testing.
val releaseKey: Triple<String, String, String>? = run {
    val props = Properties()
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    fun get(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: props.getProperty(prop)
    val file = get("HUSTLE_KEYSTORE", "storeFile") ?: return@run null
    val password = get("HUSTLE_KEYSTORE_PASSWORD", "storePassword") ?: return@run null
    Triple(file, password, get("HUSTLE_KEY_ALIAS", "keyAlias") ?: "hustlecity")
}

android {
    namespace = "com.hustlecity.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hustlecity.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "1.9.3"
    }

    signingConfigs {
        if (releaseKey != null) create("release") {
            storeFile = rootProject.file(releaseKey.first)
            storePassword = releaseKey.second
            keyAlias = releaseKey.third
            keyPassword = releaseKey.second
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKey != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// The HUD reads plain (non-snapshot) game state and refreshes on the engine's UI tick, so composables that take the
// engine must recompose with their parent instead of being skipped because the engine instance didn't change.
composeCompiler {
    featureFlags.add(org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag.StrongSkipping.disabled())
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
