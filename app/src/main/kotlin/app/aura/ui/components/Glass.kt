package app.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aura.ui.theme.AuraColors

/** Frosted glass card: tinted fill, light stroke, rounded 22dp. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    fill: Color = AuraColors.GlassFill,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, AuraColors.GlassStroke, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}
