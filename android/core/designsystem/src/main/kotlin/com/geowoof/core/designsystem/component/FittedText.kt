package com.geowoof.core.designsystem.component

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val SmallestSize = 9.sp

/**
 * A short label in a place of fixed width, like a tab or a segmented button. With a very large font it
 * shrinks to fit in place of breaking a word in two.
 */
@Composable
fun FittedText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = SmallestSize, maxFontSize = style.fontSize),
    )
}
