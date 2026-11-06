package com.geowoof.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** With real text metrics. "Guardadas", in a tab of the profile, broke in two: "Guardada" and "s". */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FittedTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(width: Int) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                Box(Modifier.width(width.dp)) {
                    FittedText("Guardadas", style = TextStyle(fontSize = 15.sp))
                }
            }
        }
    }

    private fun layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("Guardadas").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.single()
    }

    @Test
    fun without_room_it_shrinks_instead_of_breaking_the_word_or_cutting_it() {
        show(width = 48)

        val layout = layout()
        assertEquals(1, layout.lineCount)
        assertFalse(layout.hasVisualOverflow)
    }
}
