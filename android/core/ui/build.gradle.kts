plugins {
    alias(libs.plugins.geowoof.android.library.compose)
}

android {
    namespace = "com.geowoof.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.playServicesAuth)
    implementation(libs.googleId)
}
