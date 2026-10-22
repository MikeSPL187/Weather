package app.aura.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.aura.R

/** Design tokens from the «Живая диорама» style guide. */
object AuraColors {
    val Ink = Color(0xFF0A1030)
    val Panel = Color(0xFF10173A)
    val Sun = Color(0xFFFFB547)
    val OnSun = Color(0xFF1A1206)
    val Ice = Color(0xFF6FE3FF)
    val Fresh = Color(0xFF34C77B)
    val Downpour = Color(0xFF9C7BFF)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xDBFFFFFF)
    val GlassFill = Color(0x420A1432)
    val GlassStroke = Color(0x38FFFFFF)
}

val Manrope = FontFamily(
    Font(R.font.manrope_variable, FontWeight.Normal),
    Font(R.font.manrope_variable, FontWeight.Medium),
    Font(R.font.manrope_variable, FontWeight.SemiBold),
    Font(R.font.manrope_variable, FontWeight.Bold),
)

val Unbounded = FontFamily(
    Font(R.font.unbounded_variable, FontWeight.Light),
    Font(R.font.unbounded_variable, FontWeight.Medium),
)

object AuraType {
    val Temperature = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Light, fontSize = 96.sp, letterSpacing = (-0.04).em)
    val Title = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 28.sp, letterSpacing = (-0.02).em)
    val Condition = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
    val CardTitle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    val Body = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp)
    val Caption = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    val Label = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.08.em)
}

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AuraColors.Sun,
            onPrimary = AuraColors.OnSun,
            secondary = AuraColors.Ice,
            background = AuraColors.Ink,
            surface = AuraColors.Panel,
            onBackground = AuraColors.TextPrimary,
            onSurface = AuraColors.TextPrimary,
        ),
        typography = Typography(
            bodyMedium = AuraType.Body,
            bodySmall = AuraType.Caption,
            titleLarge = AuraType.Title,
            labelSmall = AuraType.Label,
        ),
        content = content,
    )
}
