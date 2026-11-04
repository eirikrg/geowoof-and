import com.android.build.api.dsl.LibraryExtension
import com.geowoof.buildlogic.bundle
import com.geowoof.buildlogic.configureKotlinAndroid
import com.geowoof.buildlogic.intVersion
import com.geowoof.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                testOptions.targetSdk = libs.intVersion("targetSdk")
                lint.targetSdk = libs.intVersion("targetSdk")
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                // Resources of ":core:designsystem" must be named "core_designsystem_*" to avoid clashes.
                resourcePrefix = path.split(":").filter(String::isNotEmpty).joinToString("_").lowercase() + "_"
            }

            dependencies {
                "testImplementation"(libs.bundle("unit-test"))
            }
        }
    }
}
