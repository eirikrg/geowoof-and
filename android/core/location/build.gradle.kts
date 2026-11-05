plugins {
    alias(libs.plugins.geowoof.android.library)
    alias(libs.plugins.geowoof.hilt)
}

android {
    namespace = "com.geowoof.core.location"
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.location)
}
