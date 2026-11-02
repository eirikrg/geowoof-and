plugins {
    alias(libs.plugins.geowoof.android.library.compose)
}

android {
    namespace = "com.geowoof.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
}
