package com.geowoof.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every test of a screen creates its ViewModel by hand, so none of them notices when Hilt does not
 * know one: the app only fails on the device, when the screen opens. It happened with a build that
 * lost the generated code of a ViewModel, so this looks for that code in what the app is built with.
 */
class HiltViewModelsTest {
    @Test
    fun every_view_model_has_its_generated_code() {
        val viewModels = viewModelsInTheSources()

        assertTrue("No ViewModels found: $viewModels", viewModels.size >= MIN_VIEW_MODELS)
        assertEquals(emptyList<String>(), viewModels.filterNot { hasGeneratedCode(it) })
    }

    private fun hasGeneratedCode(viewModel: String): Boolean = runCatching {
        Class.forName("${viewModel}_HiltModules")
    }.isSuccess

    /** Gradle runs the tests of a module from its folder. */
    private fun viewModelsInTheSources(): List<String> = File("..").resolve("feature").walkTopDown()
        .filter { it.extension == "kt" && it.invariantSeparatorsPath.contains("/src/main/") }
        .flatMap { file ->
            val source = file.readText()
            val packageName = PACKAGE.find(source)?.groupValues?.get(1)
            VIEW_MODEL.findAll(source).map { "$packageName.${it.groupValues[1]}" }
        }
        .toList()

    private companion object {
        const val MIN_VIEW_MODELS = 10
        val PACKAGE = Regex("""^package ([\w.]+)""", RegexOption.MULTILINE)
        val VIEW_MODEL = Regex("""@HiltViewModel\s+class (\w+)""")
    }
}
