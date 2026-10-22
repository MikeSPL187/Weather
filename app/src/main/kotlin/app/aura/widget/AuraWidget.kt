package app.aura.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.aura.MainActivity
import app.aura.SceneState
import app.aura.core.format.Formatters
import app.aura.core.solar.SolarCalculator
import app.aura.localNow
import java.time.LocalDateTime

/**
 * Home-screen widget: the live diorama for the first saved city with its temperature, and in
 * the larger sizes the day's range, the precipitation outlook and the next hours.
 */
class AuraWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetStore(context).load()
        provideContent {
            WidgetContent(snapshot, snapshot?.let { localNow(it.city) } ?: LocalDateTime.now())
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 230.dp)
    }
}

private val White = ColorProvider(Color.White)
private val Soft = ColorProvider(Color(0xDBFFFFFF))
private val Rain = ColorProvider(Color(0xFF9FEFFF))
private val Light = FontFamily("sans-serif-light")

private fun shapeFor(size: DpSize): WidgetShape = when {
    size.height >= AuraWidget.LARGE.height -> WidgetShape.LARGE
    size.width >= AuraWidget.WIDE.width -> WidgetShape.WIDE
    else -> WidgetShape.SMALL
}

@Composable
fun WidgetContent(snapshot: WidgetSnapshot?, now: LocalDateTime) {
    val context = LocalContext.current
    val size = LocalSize.current
    val shape = shapeFor(size)
    val density = context.resources.displayMetrics.density
    val current = snapshot?.currentAt(now)
    val scene = snapshot?.let {
        val sun = SolarCalculator.position(it.city, now.toLocalDate(), now.hour * 60.0 + now.minute)
        SceneState(current!!.second, sun, it.city)
    }
    val background = remember(scene, size, shape) { backgroundBitmap(scene, size, shape, density) }
    val rounded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceModifier.cornerRadius(WIDGET_CORNER_DP.dp) else GlanceModifier

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .then(rounded)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Image(ImageProvider(background), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
        if (snapshot == null || current == null || scene == null) {
            Column(GlanceModifier.fillMaxSize().padding(16.dp)) {
                Text("Aura", style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Text(if (shape == WidgetShape.SMALL) "Откройте Aura" else "Откройте приложение, чтобы загрузить прогноз", style = TextStyle(color = Soft, fontSize = 13.sp), maxLines = 2)
            }
            return@Box
        }
        val (temperature, condition) = current
        val description = "${snapshot.city.name}, ${Formatters.temperature(temperature)}, ${Formatters.condition(condition, scene.isNight).lowercase()}"
        val range = listOfNotNull(snapshot.maxC?.let { "↑${Formatters.temperature(it)}" }, snapshot.minC?.let { "↓${Formatters.temperature(it)}" }).joinToString(" ")
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp).semantics { contentDescription = description }) {
            Text(snapshot.city.name, style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(
                Formatters.temperature(temperature),
                style = TextStyle(color = White, fontSize = if (shape == WidgetShape.LARGE) 46.sp else 38.sp, fontFamily = Light),
                maxLines = 1,
            )
            when (shape) {
                WidgetShape.SMALL -> Unit
                // A short widget has room for one more line only.
                WidgetShape.WIDE -> Text(
                    listOf(Formatters.condition(condition, scene.isNight), range).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = TextStyle(color = Soft, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                WidgetShape.LARGE -> {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(Formatters.condition(condition, scene.isNight), style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    if (range.isNotEmpty()) Text(range, style = TextStyle(color = Soft, fontSize = 12.sp), maxLines = 1)
                }
            }
            if (shape == WidgetShape.LARGE) {
                Spacer(GlanceModifier.height(12.dp))
                Text(snapshot.headline, style = TextStyle(color = Rain, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(8.dp))
                HoursRow(snapshot, now, density)
            }
        }
    }
}

@Composable
private fun HoursRow(snapshot: WidgetSnapshot, now: LocalDateTime, density: Float) {
    val hours = snapshot.upcoming(now).take(5)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        hours.forEachIndexed { i, hour ->
            val icon = remember(hour) { iconBitmap(hour.condition, !hour.isDay, 26f, density) }
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (i == 0) "Сейчас" else Formatters.time(hour.time.toLocalTime()), style = TextStyle(color = Soft, fontSize = 11.sp), maxLines = 1)
                Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(26.dp).padding(vertical = 2.dp))
                Text(Formatters.temperature(hour.temperatureC), style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}
