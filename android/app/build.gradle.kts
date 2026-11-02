plugins {
    alias(libs.plugins.geowoof.android.application)
    alias(libs.plugins.geowoof.android.application.compose)
    alias(libs.plugins.geowoof.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.secrets)
}

// Firebase is optional (docs/decisions/0012): push notifications are only wired when the config exists.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

android {
    namespace = "com.geowoof.app"

    defaultConfig {
        applicationId = "com.geowoof.app"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The Secrets Gradle Plugin overrides this per variant, but does not reach the unit-test
        // components of AGP 9; they get the same default as local.defaults.properties.
        manifestPlaceholders["MAPS_API_KEY"] = "DEFAULT_API_KEY"
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

// Maps API key from android/secrets.properties (git-ignored); falls back to local.defaults.properties.
secrets {
    defaultPropertiesFileName = "local.defaults.properties"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.feature.map)
    implementation(projects.feature.profile)
    implementation(projects.feature.search)
    implementation(projects.feature.social)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigationSuite)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
