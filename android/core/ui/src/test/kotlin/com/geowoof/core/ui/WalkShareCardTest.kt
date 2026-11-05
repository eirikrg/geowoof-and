package com.geowoof.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.WalkStats
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** With real text metrics: what breaks here is how much room the numbers take. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkShareCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_numbers_of_a_short_walk_stay_on_one_line() {
        show(
            WalkStats(
                distanceM = 565,
                durationS = 1542,
                movingTimeS = 366,
                averageSpeedMps = 1.54,
                paceSecondsPerKm = 648,
            ),
        )

        listOf("565 m", "25:42", "6:06", "10:48 /km").forEach { assertEquals(it, 1, lineCount(it)) }
    }

    @Test
    fun the_numbers_of_a_long_walk_stay_on_one_line() {
        show(
            WalkStats(
                distanceM = 12340,
                durationS = 7509,
                movingTimeS = 7000,
                averageSpeedMps = 1.76,
                paceSecondsPerKm = 567,
            ),
        )

        listOf("12.34 km", "2:05:09", "1:56:40", "9:27 /km").forEach { assertEquals(it, 1, lineCount(it)) }
    }

    private fun show(stats: WalkStats) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                Box(Modifier.width(DIALOG_WIDTH)) {
                    WalkShareCard(
                        lines = listOf(listOf(GeoPoint(40.41, -3.68), GeoPoint(40.42, -3.67))),
                        mapSnapshot = null,
                        title = "5 Nov 2026, 10:50",
                        subtitle = "Luna, Toby",
                        stats = stats,
                    )
                }
            }
        }
    }

    private fun lineCount(text: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().lineCount
    }

    private companion object {
        /** What the dialog that shows the image leaves for it on a Pixel 9 Pro. */
        val DIALOG_WIDTH = 272.dp
    }
}
