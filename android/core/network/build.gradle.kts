plugins {
    alias(libs.plugins.geowoof.android.library)
    alias(libs.plugins.geowoof.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.geowoof.core.network"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    implementation(libs.okhttp.loggingInterceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinxSerialization)

    testImplementation(libs.okhttp.mockWebServer)
}
