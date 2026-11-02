import com.geowoof.buildlogic.forbidDependenciesOnOtherFeatures
import com.geowoof.buildlogic.library
import com.geowoof.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/** A `:feature:*` module: Compose UI, type-safe navigation and the design system; never other features. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "geowoof.android.library.compose")
            apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                "implementation"(project(":core:designsystem"))
                "implementation"(libs.library("androidx-navigation-compose"))
                "implementation"(libs.library("androidx-lifecycle-runtime-compose"))
                "implementation"(libs.library("kotlinx-serialization-json"))
            }

            forbidDependenciesOnOtherFeatures()
        }
    }
}
