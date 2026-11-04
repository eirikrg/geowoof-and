plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.secrets) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// Formatting: ktlint (Android Studio style, see .editorconfig) plus the Compose rules for ktlint.
// `**/` targets already skip the build folders of the subprojects. A targetExclude like "**/build/**" would
// walk them while other tasks write there, and fail. build-logic is an included build: it needs its own exclude.
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("build-logic/**/build/**")
        ktlint(libs.versions.ktlint.get())
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:${libs.versions.composeRulesKtlint.get()}"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("build-logic/**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}

// Static analysis for every module (and the convention plugins) in a single task: `./gradlew detekt`.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("config/detekt/detekt.yml"))
    source.setFrom(
        fileTree(rootDir) {
            include("**/src/main/kotlin/**", "**/src/test/kotlin/**", "**/src/androidTest/kotlin/**")
            exclude("**/build/**")
        },
    )
    parallel = true
}
