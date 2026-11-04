plugins {
    alias(libs.plugins.geowoof.android.library)
    alias(libs.plugins.geowoof.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.geowoof.core.datastore"

    // The failure paths log through android.util.Log, which plain JVM tests only stub.
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    implementation(projects.core.common)
    api(libs.androidx.dataStore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tink.android)
}
