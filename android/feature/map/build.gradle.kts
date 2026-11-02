plugins {
    alias(libs.plugins.geowoof.android.feature)
}

android {
    namespace = "com.geowoof.feature.map"
}

dependencies {
    implementation(libs.maps.compose)
}
