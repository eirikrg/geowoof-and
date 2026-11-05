plugins {
    alias(libs.plugins.geowoof.android.feature)
}

android {
    namespace = "com.geowoof.feature.social"
}

dependencies {
    implementation(libs.androidx.paging.compose)
}
