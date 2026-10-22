package app.aura.ui.now

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import java.time.LocalTime
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aura.core.format.Formatters
import app.aura.core.model.HourlyForecast
import app.aura.core.model.Nowcast
import app.aura.core.nowcast.DRY_THRESHOLD_MM_H
import app.aura.ui.components.WeatherIcon
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType

private val Rain = Color(0xFF6FE3FF)
private val RainHeavy = Color(0xFF9C7BFF)

/** Minute-by-minute precipitation for the next two hours as bars, with time ticks. */
@Composable
fun NowcastChart(nowcast: Nowcast, modifier: Modifier = Modifier) {
    val steps = nowcast.intensitiesMmH
    val total = steps.size * nowcast.stepMinutes
    val description = if (steps.all { it < DRY_THRESHOLD_MM_H }) "Без осадков ближайшие $total минут"
    else "График осадков на $total минут, максимум ${"%.1f".format(steps.max())} мм в час"
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val gap = 3.dp.toPx()
            val barW = (size.width - gap * (steps.size - 1)) / steps.size
            val scale = maxOf(2.5, steps.max())
            steps.forEachIndexed { i, mm ->
                val x = i * (barW + gap)
                val dry = mm < DRY_THRESHOLD_MM_H
                val hgt = if (dry) 3.dp.toPx() else (size.height * (mm / scale).toFloat()).coerceAtLeast(6.dp.toPx())
                drawRoundRect(
                    brush = if (dry) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.22f)))
                    else Brush.verticalGradient(listOf(if (mm > 2.5) RainHeavy else Rain, Rain.copy(alpha = 0.45f)), startY = size.height - hgt, endY = size.height),
                    topLeft = Offset(x, size.height - hgt),
                    size = Size(barW, hgt),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val labels = listOf(0, total / 2, total)
            labels.forEachIndexed { i, minutes ->
                Text(
                    if (minutes == 0) "Сейчас" else "+$minutes мин",
                    style = AuraType.Caption,
                    color = AuraColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        labels.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                )
            }
        }
    }
}

/**
 * The next 24 hours as a temperature curve over precipitation bars, scrolled sideways.
 * Each column holds the time, a condition orb, the temperature and the chance of rain.
 */
@Composable
fun HourlyChart(hours: List<HourlyForecast>, sunrise: LocalTime?, sunset: LocalTime?, modifier: Modifier = Modifier) {
    if (hours.isEmpty()) return
    val column = 56.dp
    val measurer = rememberTextMeasurer()
    val markerStyle = AuraType.Caption.copy(color = Color(0xFFFFD58F))
    val min = hours.minOf { it.temperatureC }
    val max = hours.maxOf { it.temperatureC }
    Box(modifier.horizontalScroll(rememberScrollState())) {
        Column {
            Row {
                hours.forEachIndexed { i, hour ->
                    Column(Modifier.width(column), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) "Сейчас" else Formatters.time(hour.time.toLocalTime()), style = AuraType.Caption, color = AuraColors.TextSecondary)
                        Box(Modifier.padding(vertical = 8.dp)) { WeatherIcon(hour.condition, isNight = !hour.isDay) }
                    }
                }
            }
            Canvas(Modifier.width(column * hours.size).height(86.dp)) {
                val colW = size.width / hours.size
                // Night hours sit on a darker band.
                val night = Color(0x1C000020)
                hours.forEachIndexed { i, hour ->
                    if (hour.isDay) return@forEachIndexed
                    val prevDay = hours.getOrNull(i - 1)?.isDay == true
                    val nextDay = hours.getOrNull(i + 1)?.isDay == true
                    // Fade in and out at dusk and dawn instead of a hard edge.
                    val colors = listOf(if (prevDay) Color.Transparent else night, night, if (nextDay) Color.Transparent else night)
                    drawRect(
                        Brush.horizontalGradient(colors, startX = colW * i, endX = colW * (i + 1)),
                        topLeft = Offset(colW * i, 12.dp.toPx()),
                        size = Size(colW, size.height - 12.dp.toPx()),
                    )
                }
                // Sunrise and sunset at their exact minute, Apple-style.
                hours.forEachIndexed { i, hour ->
                    listOfNotNull(sunrise?.let { it to "↑" }, sunset?.let { it to "↓" }).forEach { (event, arrow) ->
                        val start = hour.time.toLocalTime()
                        val minutes = (event.toSecondOfDay() - start.toSecondOfDay()) / 60
                        if (minutes in 0 until 60) {
                            val x = colW * (i + minutes / 60f)
                            drawLine(Color(0x99FFD58F), Offset(x, 14.dp.toPx()), Offset(x, size.height), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                            val label = measurer.measure("$arrow${Formatters.time(event)}", markerStyle)
                            drawText(label, topLeft = Offset((x - label.size.width / 2).coerceIn(0f, size.width - label.size.width), 0f))
                        }
                    }
                }
                val labelSpace = 20.dp.toPx()
                val curveTop = labelSpace
                val curveBottom = size.height * 0.62f
                val span = (max - min).coerceAtLeast(1.0)
                val points = hours.mapIndexed { i, hour ->
                    Offset(colW * (i + 0.5f), (curveBottom - (hour.temperatureC - min) / span * (curveBottom - curveTop)).toFloat())
                }
                val curve = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val a = points[i - 1]
                        val b = points[i]
                        val mid = (a.x + b.x) / 2
                        cubicTo(mid, a.y, mid, b.y, b.x, b.y)
                    }
                }
                val fill = Path().apply {
                    addPath(curve)
                    lineTo(points.last().x, curveBottom + 10.dp.toPx())
                    lineTo(points.first().x, curveBottom + 10.dp.toPx())
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(AuraColors.Sun.copy(alpha = 0.28f), Color.Transparent), startY = curveTop, endY = curveBottom + 10.dp.toPx()))
                drawPath(curve, Brush.horizontalGradient(listOf(AuraColors.Sun, Color(0xFFFFE3A0))), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                points.forEachIndexed { i, p ->
                    drawCircle(Color.White, radius = if (i == 0) 4.dp.toPx() else 2.5.dp.toPx(), center = p)
                }
                // Precipitation bars along the bottom.
                val barMax = size.height - curveBottom - 14.dp.toPx()
                hours.forEachIndexed { i, hour ->
                    if (hour.precipitationChancePercent >= 20) {
                        val hgt = barMax * hour.precipitationChancePercent / 100f
                        drawRoundRect(
                            Rain.copy(alpha = 0.35f + 0.5f * hour.precipitationChancePercent / 100f),
                            topLeft = Offset(colW * i + colW * 0.3f, size.height - hgt),
                            size = Size(colW * 0.4f, hgt),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }
            }
            Row(Modifier.padding(top = 2.dp)) {
                hours.forEach { hour ->
                    Column(Modifier.width(column), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Formatters.temperature(hour.temperatureC), style = AuraType.CardTitle, color = AuraColors.TextPrimary)
                        Text(
                            if (hour.precipitationChancePercent >= 20) "${hour.precipitationChancePercent} %" else " ",
                            style = AuraType.Caption,
                            color = Rain,
                        )
                    }
                }
            }
        }
    }
}
