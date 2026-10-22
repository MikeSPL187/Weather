package app.aura.ui.forecast

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aura.WeatherUiState
import app.aura.core.format.Formatters
import app.aura.core.model.DailyForecast
import app.aura.ui.components.ConditionOrb
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType

@Composable
fun ForecastScreen(state: WeatherUiState, contentPadding: PaddingValues) {
    val forecast = state.forecast
    var open by remember { mutableIntStateOf(0) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(state.city.name, style = AuraType.Caption, color = AuraColors.TextSecondary)
                Text("10 дней", style = AuraType.Title, color = AuraColors.TextPrimary)
            }
        }
        if (forecast == null) return@LazyColumn
        val min = forecast.daily.minOf { it.minC }
        val max = forecast.daily.maxOf { it.maxC }
        itemsIndexed(forecast.daily) { index, day ->
            DayRow(
                day = day,
                label = Formatters.dayLabel(day.date, state.localTime.toLocalDate()),
                rangeMin = min,
                rangeMax = max,
                expanded = open == index,
                onToggle = { open = if (open == index) -1 else index },
            )
        }
    }
}

private fun temperatureColor(c: Double): Color = when {
    c <= 0 -> Color(0xFF9CC9FF)
    c <= 6 -> Color(0xFF6FE3FF)
    c <= 12 -> Color(0xFFB6F09C)
    c <= 17 -> Color(0xFFFFD56B)
    else -> Color(0xFFFF9F43)
}

@Composable
private fun DayRow(
    day: DailyForecast,
    label: String,
    rangeMin: Double,
    rangeMax: Double,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (expanded) 0.12f else 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .clickable(onClickLabel = if (expanded) "Свернуть" else "Подробнее", onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто" }
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(74.dp)) {
                Text(label, style = AuraType.CardTitle, color = AuraColors.TextPrimary)
                Text(Formatters.shortDate(day.date), style = AuraType.Caption, color = AuraColors.TextSecondary)
            }
            ConditionOrb(day.condition, size = 28.dp)
            Text(
                if (day.precipitationChancePercent > 0) "${day.precipitationChancePercent} %" else "",
                style = AuraType.Caption,
                color = Color(0xFF9FEFFF),
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End,
            )
            Text(Formatters.temperature(day.minC), style = AuraType.Body, color = AuraColors.TextSecondary, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
            Canvas(Modifier.weight(1f).height(6.dp).padding(horizontal = 8.dp)) {
                val span = (rangeMax - rangeMin).coerceAtLeast(1.0)
                val start = ((day.minC - rangeMin) / span).toFloat() * size.width
                val end = ((day.maxC - rangeMin) / span).toFloat() * size.width
                drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(temperatureColor(day.minC), temperatureColor(day.maxC)), startX = start, endX = end),
                    topLeft = Offset(start, 0f),
                    size = Size(end - start, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
            Text(Formatters.temperature(day.maxC), style = AuraType.CardTitle, color = AuraColors.TextPrimary, modifier = Modifier.width(34.dp))
        }
        if (expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(day.summary, style = AuraType.Body, color = AuraColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat("Ветер", "${day.windSpeedMs.toInt()} м/с", Modifier.weight(1f))
                    Stat("УФ-индекс", "${day.uvIndex}", Modifier.weight(1f))
                    Stat("Влажность", "${day.humidityPercent} %", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = AuraType.Caption, color = AuraColors.TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = AuraType.CardTitle, color = AuraColors.TextPrimary)
    }
}
