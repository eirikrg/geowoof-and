package com.geowoof.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** SDK levels, Java/Kotlin targets, warnings as errors and lint, shared by every Android module. */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = libs.intVersion("compileSdk")
        defaultConfig.minSdk = libs.intVersion("minSdk")
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.animationsDisabled = true
        configureLint()
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            allWarningsAsErrors = true
            // Flow operators and coroutine test APIs (setMain, runTest dispatchers) are still experimental.
            freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }

    // Hilt generates test sources even in modules without tests of their own, and Gradle fails when
    // it finds sources but no tests. Modules with written tests keep that check: it catches a
    // misconfigured test runner.
    val hasWrittenTests = file("src/test").exists()
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests.set(hasWrittenTests)
        // Robolectric needs access to JDK internals to emulate Android 16+ (shared memory file descriptors).
        jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
    }
}

private fun CommonExtension.configureLint() {
    lint.apply {
        warningsAsErrors = true
        abortOnError = true
        checkTestSources = true
        // Version-update checks would break the build whenever a library releases; Dependabot covers them.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // Lint checks bundled in some AndroidX libraries target an older Lint API; Lint skips them and
        // reports it, which is not actionable in this project.
        disable += "ObsoleteLintCustomCheck"
    }
}
