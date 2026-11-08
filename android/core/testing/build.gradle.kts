plugins {
    alias(libs.plugins.geowoof.android.library)
}

android {
    namespace = "com.geowoof.core.testing"
}

dependencies {
    api(projects.core.data)
    api(projects.core.location)
    api(projects.core.model)
    implementation(libs.kotlinx.serialization.json)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.androidx.paging.common)
    implementation(libs.robolectric)
}
