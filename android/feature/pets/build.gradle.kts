plugins {
    alias(libs.plugins.geowoof.android.feature)
}

android {
    namespace = "com.geowoof.feature.pets"
}

dependencies {
    implementation(libs.vico.compose.m3)
}
