plugins {
    alias(libs.plugins.geowoof.android.library)
    alias(libs.plugins.geowoof.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.geowoof.core.database"
}

// Schemas are kept in git so that future migrations can be written and tested against them.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(projects.core.common)
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
