package com.geowoof.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * Feature modules must not depend on each other; only `:app` wires them
 * together through navigation callbacks. Fails the build as soon as such a dependency is declared.
 */
internal fun Project.forbidDependenciesOnOtherFeatures() {
    val featurePath = path
    configurations.configureEach {
        dependencies.configureEach {
            if (this is ProjectDependency && path.startsWith(":feature:") && path != featurePath) {
                throw GradleException(
                    "$featurePath must not depend on $path: feature modules are independent.",
                )
            }
        }
    }
}
