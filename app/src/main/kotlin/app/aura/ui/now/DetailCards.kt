package app.aura.ui.now

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.aura.WeatherUiState
import app.aura.core.format.Formatters
import app.aura.ui.components.GlassCard
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
private fun DetailCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier.heightIn(min = 150.dp).fillMaxHeight()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title.uppercase(), style = AuraType.Label, color = AuraColors.TextSecondary)
            content()
        }
    }
}

@Composable
private fun BigValue(value: String, unit: String = "") {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = AuraType.Title, color = AuraColors.TextPrimary)
        if (unit.isNotEmpty()) Text(" $unit", style = AuraType.Caption, color = AuraColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
    }
}

private fun uvLabel(uv: Double) = when {
    uv < 3 -> "Низкий"
    uv < 6 -> "Умеренный"
    uv < 8 -> "Высокий"
    uv < 11 -> "Очень высокий"
    else -> "Экстремальный"
}

private fun aqiLabel(aqi: Int) = when {
    aqi <= 20 -> "Отличный"
    aqi <= 40 -> "Хороший"
    aqi <= 60 -> "Умеренный"
    aqi <= 80 -> "Плохой"
    else -> "Очень плохой"
}

/** The detail grid under the hourly chart: two columns of cards, the sun path across both. */
@Composable
fun DetailCards(state: WeatherUiState, modifier: Modifier = Modifier) {
    val forecast = state.forecast ?: return
    val now = forecast.current
    val today = forecast.daily.firstOrNull()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailCard("Ветер", Modifier.weight(1f)) {
                WindCompass(now.windDirectionDeg, Modifier.size(84.dp).align(Alignment.CenterHorizontally))
                BigValue("${now.windSpeedMs.roundToInt()}", "м/с · ${Formatters.windDirection(now.windDirectionDeg)}")
                now.windGustMs?.let { Text("Порывы до ${it.roundToInt()} м/с", style = AuraType.Caption, color = AuraColors.TextSecondary) }
            }
            DetailCard("УФ-индекс", Modifier.weight(1f)) {
                BigValue("${now.uvIndex.roundToInt()}")
                Text(uvLabel(now.uvIndex), style = AuraType.CardTitle, color = AuraColors.TextPrimary)
                ScaleBar(
                    fraction = (now.uvIndex / 11).toFloat(),
                    colors = listOf(Color(0xFF34C77B), Color(0xFFFFD56B), Color(0xFFFF9F43), Color(0xFFFF5D5D), Color(0xFFB76BFF)),
                )
                Text(
                    if ((today?.uvIndexMax ?: 0.0) >= 3) "Днём до ${today!!.uvIndexMax.roundToInt()}: нужен крем SPF 30" else "Защита от солнца сегодня не нужна",
                    style = AuraType.Caption, color = AuraColors.TextSecondary,
                )
            }
        }
        state.sunTimes?.let { sun ->
            DetailCard("Солнце", Modifier.fillMaxWidth()) {
                SunPath(sun.sunrise, sun.sunset, state.localTime.toLocalTime(), Modifier.fillMaxWidth().height(86.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Восход ${sun.sunrise?.let(Formatters::time) ?: "—"}", style = AuraType.Body, color = AuraColors.TextPrimary)
                    val rise = sun.sunrise
                    val set = sun.sunset
                    val length = if (rise != null && set != null) {
                        val minutes = (set.toSecondOfDay() - rise.toSecondOfDay()) / 60
                        "${minutes / 60} ч ${minutes % 60} мин"
                    } else "—"
                    Text(length, style = AuraType.Caption, color = AuraColors.TextSecondary)
                    Text("Закат ${sun.sunset?.let(Formatters::time) ?: "—"}", style = AuraType.Body, color = AuraColors.TextPrimary)
                }
            }
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailCard("Ощущается", Modifier.weight(1f)) {
                BigValue(Formatters.temperature(now.feelsLikeC))
                val diff = now.feelsLikeC - now.temperatureC
                Text(
                    when {
                        diff <= -3 -> "Холоднее из-за ветра и влажности"
                        diff >= 3 -> "Теплее из-за влажности"
                        else -> "Близко к фактической температуре"
                    },
                    style = AuraType.Caption, color = AuraColors.TextSecondary,
                )
            }
            DetailCard("Влажность", Modifier.weight(1f)) {
                BigValue("${now.humidityPercent}", "%")
                ScaleBar(now.humidityPercent / 100f, listOf(Color(0xFF6FE3FF).copy(alpha = 0.5f), Color(0xFF6FE3FF)))
                now.dewPointC?.let { Text("Точка росы ${Formatters.temperature(it)}", style = AuraType.Caption, color = AuraColors.TextSecondary) }
            }
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailCard("Давление", Modifier.weight(1f)) {
                PressureGauge(now.pressureMmHg, Modifier.fillMaxWidth().height(52.dp))
                BigValue("${now.pressureMmHg}", "мм рт. ст.")
                Text(
                    when {
                        now.pressureMmHg < 740 -> "Пониженное"
                        now.pressureMmHg > 765 -> "Повышенное"
                        else -> "В пределах нормы"
                    },
                    style = AuraType.Caption, color = AuraColors.TextSecondary,
                )
            }
            val next24 = forecast.hourly.take(24)
            val total = next24.sumOf { it.precipitationMm }
            val firstWet = next24.firstOrNull { it.precipitationMm >= 0.1 || it.precipitationChancePercent >= 50 }
            DetailCard("Осадки", Modifier.weight(1f)) {
                BigValue(if (total < 0.05) "0" else "%.1f".format(total), "мм за 24 ч")
                Text(
                    when {
                        firstWet == null -> "Сухо ближайшие сутки"
                        firstWet == next24.first() -> "Идут сейчас"
                        else -> "Ожидаются с ${Formatters.time(firstWet.time.toLocalTime())}"
                    },
                    style = AuraType.Caption, color = AuraColors.TextSecondary,
                )
                PrecipitationStrip(next24.map { it.precipitationChancePercent })
            }
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            now.airQualityIndex?.let { aqi ->
                DetailCard("Воздух", Modifier.weight(1f)) {
                    AqiRing(aqi, Modifier.size(64.dp))
                    Text(aqiLabel(aqi), style = AuraType.CardTitle, color = AuraColors.TextPrimary)
                    Text("Европейский индекс AQI", style = AuraType.Caption, color = AuraColors.TextSecondary)
                }
            }
            DetailCard("Видимость", Modifier.weight(1f)) {
                val km = now.visibilityKm
                BigValue(km?.let { if (it < 1) "%.1f".format(it) else "${it.roundToInt()}" } ?: "—", "км")
                Text(
                    when {
                        km == null -> "Нет данных"
                        km < 1 -> "Густой туман, будьте осторожны на дороге"
                        km < 5 -> "Дымка или осадки"
                        km < 20 -> "Хорошая видимость"
                        else -> "Отличная видимость"
                    },
                    style = AuraType.Caption, color = AuraColors.TextSecondary,
                )
            }
        }
    }
}

/** Chance of precipitation for each of the next 24 hours as a thin bar strip. */
@Composable
private fun PrecipitationStrip(chances: List<Int>) {
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        if (chances.isEmpty()) return@Canvas
        val gap = 1.5.dp.toPx()
        val w = (size.width - gap * (chances.size - 1)) / chances.size
        chances.forEachIndexed { i, chance ->
            val h = (size.height * chance / 100f).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                Color(0xFF6FE3FF).copy(alpha = if (chance >= 30) 0.85f else 0.3f),
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun ScaleBar(fraction: Float, colors: List<Color>) {
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val barH = 6.dp.toPx()
        val y = (size.height - barH) / 2
        drawRoundRect(Brush.horizontalGradient(colors), topLeft = Offset(0f, y), size = Size(size.width, barH), cornerRadius = CornerRadius(barH / 2))
        val x = size.width * fraction.coerceIn(0f, 1f)
        drawCircle(Color(0xFF0A1030), radius = size.height / 2 + 1.dp.toPx(), center = Offset(x, size.height / 2))
        drawCircle(Color.White, radius = size.height / 2 - 1.dp.toPx(), center = Offset(x, size.height / 2))
    }
}

@Composable
private fun WindCompass(directionDeg: Int, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "Ветер ${Formatters.windDirection(directionDeg)}" }) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f)), center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.3f), radius = r, center = c)
        for (i in 0 until 36) {
            val a = i * 10 * PI / 180
            val inner = if (i % 9 == 0) r * 0.74f else r * 0.84f
            drawLine(
                Color.White.copy(alpha = if (i % 9 == 0) 0.7f else 0.3f),
                c + Offset((sin(a) * inner).toFloat(), (-cos(a) * inner).toFloat()),
                c + Offset((sin(a) * r * 0.94f).toFloat(), (-cos(a) * r * 0.94f).toFloat()),
                strokeWidth = 1.2.dp.toPx(),
            )
        }
        // The arrow points where the wind blows to.
        rotate((directionDeg + 180).toFloat(), c) {
            val arrow = Path().apply {
                moveTo(c.x, c.y - r * 0.62f)
                lineTo(c.x + r * 0.14f, c.y - r * 0.3f)
                lineTo(c.x + r * 0.04f, c.y - r * 0.3f)
                lineTo(c.x + r * 0.04f, c.y + r * 0.5f)
                lineTo(c.x - r * 0.04f, c.y + r * 0.5f)
                lineTo(c.x - r * 0.04f, c.y - r * 0.3f)
                lineTo(c.x - r * 0.14f, c.y - r * 0.3f)
                close()
            }
            drawPath(arrow, AuraColors.Sun)
        }
        drawCircle(Color.White, radius = r * 0.08f, center = c)
    }
}

@Composable
private fun SunPath(sunrise: LocalTime?, sunset: LocalTime?, now: LocalTime, modifier: Modifier) {
    Canvas(modifier) {
        val base = size.height - 6.dp.toPx()
        val left = 12.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val arcH = size.height - 14.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
        val arc = Path().apply {
            moveTo(left, base)
            cubicTo(left + (right - left) * 0.2f, base - arcH * 1.3f, right - (right - left) * 0.2f, base - arcH * 1.3f, right, base)
        }
        drawPath(arc, Color.White.copy(alpha = 0.3f), style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 10f))))
        if (sunrise == null || sunset == null) return@Canvas
        val t = ((now.toSecondOfDay() - sunrise.toSecondOfDay()).toFloat() / (sunset.toSecondOfDay() - sunrise.toSecondOfDay())).coerceIn(0f, 1f)
        // Cubic Bezier point at t for the arc above.
        fun bez(p0: Float, p1: Float, p2: Float, p3: Float) =
            (1 - t) * (1 - t) * (1 - t) * p0 + 3 * (1 - t) * (1 - t) * t * p1 + 3 * (1 - t) * t * t * p2 + t * t * t * p3
        val x = bez(left, left + (right - left) * 0.2f, right - (right - left) * 0.2f, right)
        val y = bez(base, base - arcH * 1.3f, base - arcH * 1.3f, base)
        val up = now.isAfter(sunrise) && now.isBefore(sunset)
        if (up) {
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFD678), Color.Transparent), center = Offset(x, y), radius = 22.dp.toPx()), radius = 22.dp.toPx(), center = Offset(x, y))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF8D6), Color(0xFFFFC94A), Color(0xFFE2701A)), center = Offset(x - 3.dp.toPx(), y - 3.dp.toPx()), radius = 12.dp.toPx()), radius = 9.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun PressureGauge(mmHg: Int, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 7.dp.toPx()
        val d = minOf(size.width, size.height * 2) - stroke
        val topLeft = Offset((size.width - d) / 2, stroke / 2)
        drawArc(Color.White.copy(alpha = 0.16f), 180f, 180f, false, topLeft = topLeft, size = Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        val fraction = ((mmHg - 720) / 60f).coerceIn(0f, 1f)
        drawArc(AuraColors.Ice, 180f, 180f * fraction, false, topLeft = topLeft, size = Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun AqiRing(aqi: Int, modifier: Modifier) {
    val color = when {
        aqi <= 40 -> AuraColors.Fresh
        aqi <= 60 -> Color(0xFFFFD56B)
        else -> Color(0xFFFF7A5A)
    }
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(64.dp)) {
            val stroke = 7.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.14f), -90f, 360f, false, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            drawArc(color, -90f, 360f * (aqi / 100f).coerceIn(0.02f, 1f), false, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$aqi", style = AuraType.CardTitle, color = AuraColors.TextPrimary)
    }
    Spacer(Modifier.height(2.dp))
}
