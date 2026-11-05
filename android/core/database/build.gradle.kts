plugins {
    alias(libs.plugins.geowoof.android.library)
    alias(libs.plugins.geowoof.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.geowoof.core.database"

    // MigrationTest opens the exported schemas as assets.
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

// Schemas are kept in git so that future migrations can be written and tested against them.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(projects.core.common)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
