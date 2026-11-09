import com.geowoof.buildlogic.bundle
import com.geowoof.buildlogic.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** A pure Kotlin module (no Android), such as `:core:model`. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.jvm")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_17
                    allWarningsAsErrors = true
                }
            }

            dependencies {
                "testImplementation"(libs.bundle("unit-test"))
            }

            // The CI runs testDebugUnitTest in every module, which here is called test: without this, the tests of
            // these modules never ran there (2026-11-09).
            tasks.register("testDebugUnitTest") {
                group = "verification"
                description = "Runs the unit tests, under the name that the Android modules use."
                dependsOn("test")
            }
        }
    }
}
