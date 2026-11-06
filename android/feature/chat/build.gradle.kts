plugins {
    alias(libs.plugins.geowoof.android.feature)
}

android {
    namespace = "com.geowoof.feature.chat"
}

dependencies {
    implementation(libs.androidx.paging.compose)
}
