package com.geowoof.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Enables Compose and adds the BOM, tooling and the Robolectric-based Compose test stack. */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    commonExtension.buildFeatures.compose = true

    dependencies {
        val bom = platform(libs.library("androidx-compose-bom"))
        "implementation"(bom)
        "testImplementation"(bom)
        "androidTestImplementation"(bom)
        "implementation"(libs.library("androidx-compose-ui-tooling-preview"))
        "debugImplementation"(libs.library("androidx-compose-ui-tooling"))
        "debugImplementation"(libs.library("androidx-compose-ui-test-manifest"))
        "testImplementation"(libs.bundle("compose-ui-test"))
    }
}
