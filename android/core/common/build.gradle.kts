plugins {
    alias(libs.plugins.geowoof.android.library)
    alias(libs.plugins.geowoof.hilt)
}

android {
    namespace = "com.geowoof.core.common"
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
}
