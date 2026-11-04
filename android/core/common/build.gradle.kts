plugins {
    alias(libs.plugins.geowoof.android.library)
    alias(libs.plugins.geowoof.hilt)
}

android {
    namespace = "com.geowoof.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
