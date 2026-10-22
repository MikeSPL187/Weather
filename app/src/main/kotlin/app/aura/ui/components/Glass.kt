package app.aura.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aura.core.model.Condition
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

/** Small shaded sphere used as the weather icon in lists, matching the 3D scene objects. */
@Composable
fun ConditionOrb(condition: Condition, isNight: Boolean = false, size: Dp = 24.dp) {
    val colors = when {
        condition == Condition.CLEAR && isNight -> listOf(Color(0xFFFFFFFF), Color(0xFFE6E9F5), Color(0xFFA9B0CC))
        condition == Condition.CLEAR -> listOf(Color(0xFFFFF4C2), Color(0xFFFFC94A), Color(0xFFF08A1C))
        condition == Condition.CLOUDY || condition == Condition.FOG -> listOf(Color.White, Color(0xFFDDE5F5), Color(0xFFA7B4CF))
        condition == Condition.SNOW -> listOf(Color.White, Color(0xFFEAF2FF), Color(0xFFB8C8E6))
        else -> listOf(Color(0xFFC9D3E6), Color(0xFF8592AD), Color(0xFF4B5670))
    }
    Canvas(modifier = Modifier.size(size)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = colors,
                center = Offset(this.size.width * 0.35f, this.size.height * 0.3f),
                radius = this.size.minDimension * 0.8f,
            ),
        )
    }
}
