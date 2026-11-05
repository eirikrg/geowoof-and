plugins {
    alias(libs.plugins.geowoof.android.library)
    alias(libs.plugins.geowoof.hilt)
}

android {
    namespace = "com.geowoof.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.androidx.exifinterface)
    api(libs.androidx.paging.common)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
