import com.geowoof.buildlogic.library
import com.geowoof.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/**
 * Several KSP runs at the same time can lose the classes of their dependencies. Sometimes they fail, and
 * sometimes they succeed without generating anything, and the app only fails when that screen opens.
 */
abstract class KspQueue : BuildService<BuildServiceParameters.None>

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

            val queue = gradle.sharedServices.registerIfAbsent("kspQueue", KspQueue::class.java) {
                maxParallelUsages.set(1)
            }
            tasks.configureEach {
                if (name.startsWith("ksp")) usesService(queue)
            }
        }
    }
}
