plugins {
    alias(libs.plugins.geowoof.android.library)
    alias(libs.plugins.geowoof.hilt)
}

android {
    namespace = "com.geowoof.core.domain"
}

dependencies {
    api(projects.core.data)
    api(projects.core.location)
    api(projects.core.model)

    testImplementation(projects.core.testing)
}
