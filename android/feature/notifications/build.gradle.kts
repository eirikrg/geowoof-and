plugins {
    alias(libs.plugins.geowoof.android.feature)
}

android {
    namespace = "com.geowoof.feature.notifications"
}

dependencies {
    implementation(libs.androidx.paging.compose)
}
