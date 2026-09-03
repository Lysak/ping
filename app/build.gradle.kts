import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.android.junit5)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps =
    Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }

android {
    namespace = "com.lysak.ping"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lysak.ping"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (keystoreProps.isNotEmpty()) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint {
        warningsAsErrors = true
        // Dependabot / Renovate owns version bumps, not lint.
        disable += listOf("GradleDependency", "AndroidGradlePluginVersion")
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Parallelise the IR backend (bytecode gen) across cores. 0 = one thread per core.
        freeCompilerArgs.add("-Xbackend-threads=0")
    }
}

// Konsist architecture scan is single-threaded and slow, so keep it out of the
// fast `make gate` loop: it runs only when `-PwithArchTest` is passed
// (`make test` / `make verify`).
tasks.withType<Test>().configureEach {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    // Hard ceiling: kills the worker JVM if a test hangs or spins (e.g. a bad
    // coroutine loop), so `make gate` always terminates instead of running forever.
    timeout.set(Duration.ofMinutes(2))
    if (!project.hasProperty("withArchTest")) {
        filter {
            excludeTestsMatching("com.lysak.ping.architecture.*")
            isFailOnNoMatchingTests = false
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    parallel = true
    // formatting auto-fix is Spotless/ktlint's job; detekt only reports.
}
dependencies {
    detektPlugins(libs.detekt.compose)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.5.0").editorConfigOverride(
            mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable,Preview"),
        )
        licenseHeader("// SPDX-License-Identifier: GPL-3.0-or-later")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.5.0").editorConfigOverride(
            mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable,Preview"),
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    // Vintage engine: existing JUnit 4 tests keep running on the JUnit 5
    // platform while they migrate to Jupiter one file at a time. New tests: Jupiter.
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.konsist)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
