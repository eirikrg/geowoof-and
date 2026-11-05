plugins {
    alias(libs.plugins.geowoof.android.library)
}

android {
    namespace = "com.geowoof.core.testing"

    // The polyline vectors and GPX traces shared with the backend tests.
    sourceSets.getByName("main").resources.directories.add("../../../docs/fixtures")
}

dependencies {
    api(projects.core.data)
    api(projects.core.location)
    api(projects.core.model)
    implementation(libs.kotlinx.serialization.json)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
