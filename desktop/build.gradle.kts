import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

// The desktop game is built from the Android app's own sources. These few files only make sense on Android; the
// desktop module has its own versions of them (under different file names, so this filter leaves those alone).
val androidOnly = listOf(
    "com/hustlecity/game/MainActivity.kt",
    "com/hustlecity/game/render/gpu/GlWorldView.kt",
    "com/hustlecity/game/ui/GpuHost.kt",
)

sourceSets.main {
    kotlin.srcDir("../app/src/main/java")
    kotlin.exclude(androidOnly)
    // the photo materials and anything else the game loads as a resource
    resources.srcDir("../app/src/main/resources")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Same as the Android app: the HUD reads plain game state and refreshes on the engine's UI tick.
composeCompiler {
    featureFlags.add(org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag.StrongSkipping.disabled())
}

val lwjglNatives = listOf("natives-windows", "natives-windows-arm64", "natives-linux", "natives-macos", "natives-macos-arm64")

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // OpenGL for the HD renderer
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.6"))
    for (module in listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl")) {
        implementation("org.lwjgl:$module")
        for (natives in lwjglNatives) runtimeOnly("org.lwjgl:$module::$natives")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation(compose.desktop.uiTestJUnit4)
}

compose.desktop {
    application {
        mainClass = "com.hustlecity.game.desktop.MainKt"
        jvmArgs += listOf("-Xmx2g")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "HustleCity"
            // Installers need a plain number: keep it in step with versionName in app/build.gradle.kts.
            packageVersion = "1.7.0"
            description = "Hustle City - a business life simulation"
            vendor = "Hustle City"
            copyright = "Hustle City is an independent fan project."
            modules("java.desktop", "java.instrument", "jdk.unsupported")

            windows {
                iconFile.set(project.file("icons/hustle-city.ico"))
                menuGroup = "Hustle City"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                // Lets a newer installer replace an older install: never change it.
                upgradeUuid = "6a1f8d0e-4c1b-4f4a-9b7e-2f6d3c8a5e11"
            }
            linux {
                iconFile.set(project.file("icons/hustle-city.png"))
            }
        }

        buildTypes.release.proguard {
            // Saves are kotlinx.serialization JSON; shrinking buys little on a desktop and risks breaking them.
            isEnabled.set(false)
        }
    }
}

tasks.test {
    // Headless render checks write their pictures here.
    systemProperty("snapshots.dir", layout.buildDirectory.dir("snapshots").get().asFile.absolutePath)
    // -PgpuTest also runs the HD renderer through OpenGL (needs a display and an OpenGL 3.3 driver)
    if (project.hasProperty("gpuTest")) systemProperty("hustle.gpuTest", "true")
    // -PcinemaIn=... renders recorded trailer shots with the desktop GL (see CinemaRender)
    for ((k, prop) in listOf("cinemaIn" to "cinema.in", "cinemaOut" to "cinema.out", "cinemaShots" to "cinema.shots", "cinemaFrames" to "cinema.frames", "cinemaPlan" to "cinema.plan"))
        project.findProperty(k)?.let { systemProperty(prop, rootProject.file(it.toString()).takeIf { _ -> k == "cinemaIn" || k == "cinemaOut" || k == "cinemaPlan" }?.absolutePath ?: it.toString()) }
    testLogging { showStandardStreams = true }
}
