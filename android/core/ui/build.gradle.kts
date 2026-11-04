plugins {
    alias(libs.plugins.geowoof.android.library.compose)
}

android {
    namespace = "com.geowoof.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.credentials.playServicesAuth)
    implementation(libs.googleId)
}
