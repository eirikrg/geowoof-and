import java.util.Properties

plugins {
    alias(libs.plugins.geowoof.android.application)
    alias(libs.plugins.geowoof.android.application.compose)
    alias(libs.plugins.geowoof.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.secrets)
}

// Firebase is optional: push notifications are only wired when the config exists.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// The version is the last tag vX.Y.Z, which the Release workflow publishes when asked. Between two tags it
// reads like 0.9.1-3-gabc1234. GEOWOOF_VERSION wins, and without git or tags it is 0.0.0.
val appVersion: String = providers.environmentVariable("GEOWOOF_VERSION").orNull
    ?: runCatching {
        providers.exec {
            commandLine("git", "describe", "--tags", "--match", "v[0-9]*")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().removePrefix("v")
    }.getOrDefault("").ifBlank { "0.0.0" }

// Always higher for a higher version, as Android requires for an update: 0.9.1 is 9001 and 1.0.0 is 1000000.
val appVersionCode: Int = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(appVersion)?.destructured
    ?.let { (major, minor, patch) -> major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt() }
    ?.coerceAtLeast(1)
    ?: 1

android {
    namespace = "com.geowoof.app"

    defaultConfig {
        applicationId = "com.geowoof.app"
        versionCode = appVersionCode
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The Secrets Gradle Plugin overrides this per variant, but does not reach the unit-test
        // components of AGP 9; they get the same default as local.defaults.properties.
        manifestPlaceholders["MAPS_API_KEY"] = "DEFAULT_API_KEY"
        // Optional and empty by default, which the Secrets Gradle Plugin cannot turn into a field.
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${secretProperty("GOOGLE_SERVER_CLIENT_ID")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        // Per-app language selection (Android 13+) for the locales in the values* folders.
        generateLocaleConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Keys and URLs from android/secrets.properties (git-ignored), falling back to local.defaults.properties:
// the plugin exposes each one as a BuildConfig field and a manifest placeholder.
secrets {
    // The plugin reads local.properties unless told otherwise.
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.defaults.properties"
    ignoreList.add("GOOGLE_SERVER_CLIENT_ID")
}

/** Value from secrets.properties, else from local.defaults.properties; empty if neither has it. */
fun secretProperty(name: String): String = listOf("secrets.properties", "local.defaults.properties")
    .map(rootProject::file)
    .filter { it.exists() }
    .firstNotNullOfOrNull { file -> Properties().apply { file.inputStream().use(::load) }.getProperty(name)?.trim() }
    .orEmpty()

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.notifications)
    implementation(projects.core.ui)
    implementation(projects.feature.auth)
    implementation(projects.feature.chat)
    implementation(projects.feature.map)
    implementation(projects.feature.notifications)
    implementation(projects.feature.pets)
    implementation(projects.feature.profile)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)
    implementation(projects.feature.social)
    implementation(projects.feature.walks)
    implementation(projects.sync.work)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigationSuite)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}
