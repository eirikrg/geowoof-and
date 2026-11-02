import com.geowoof.buildlogic.library
import com.geowoof.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/** Hilt with KSP for an Android module. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.google.devtools.ksp")
            apply(plugin = "com.google.dagger.hilt.android")

            dependencies {
                "implementation"(libs.library("hilt-android"))
                "ksp"(libs.library("hilt-compiler"))
            }
        }
    }
}
