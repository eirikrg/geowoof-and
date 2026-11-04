import com.geowoof.buildlogic.bundle
import com.geowoof.buildlogic.forbidDependenciesOnOtherFeatures
import com.geowoof.buildlogic.library
import com.geowoof.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/**
 * A `:feature:*` module: Compose UI with Hilt ViewModels, type-safe navigation, the design system and
 * the core layers (domain, data, model); never other features.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "geowoof.android.library.compose")
            apply(plugin = "geowoof.hilt")
            apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                "implementation"(project(":core:designsystem"))
                "implementation"(project(":core:ui"))
                "implementation"(project(":core:domain"))
                "implementation"(project(":core:data"))
                "implementation"(project(":core:model"))
                "implementation"(libs.library("androidx-navigation-compose"))
                "implementation"(libs.library("androidx-lifecycle-runtime-compose"))
                "implementation"(libs.library("androidx-lifecycle-viewModelCompose"))
                "implementation"(libs.library("androidx-hilt-lifecycle-viewModelCompose"))
                "implementation"(libs.library("kotlinx-serialization-json"))

                "testImplementation"(project(":core:testing"))
                "testImplementation"(libs.bundle("unit-test"))
            }

            forbidDependenciesOnOtherFeatures()
        }
    }
}
