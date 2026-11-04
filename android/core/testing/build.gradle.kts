plugins {
    alias(libs.plugins.geowoof.android.library)
}

android {
    namespace = "com.geowoof.core.testing"
}

dependencies {
    api(projects.core.data)
    api(projects.core.model)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
