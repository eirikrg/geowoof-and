plugins {
    alias(libs.plugins.geowoof.android.feature)
}

android {
    namespace = "com.geowoof.feature.walks"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.paging.compose)
    implementation(libs.maps.compose)
}
