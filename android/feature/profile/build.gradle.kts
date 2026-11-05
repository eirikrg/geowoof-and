plugins {
    alias(libs.plugins.geowoof.android.feature)
}

android {
    namespace = "com.geowoof.feature.profile"
}

dependencies {
    implementation(libs.androidx.paging.compose)
}
