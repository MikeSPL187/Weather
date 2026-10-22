package app.aura.ui.now

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aura.WeatherUiState
import app.aura.core.format.Formatters
import app.aura.core.model.Condition
import app.aura.ui.components.AuraIcons
import app.aura.ui.components.ConditionOrb
import app.aura.ui.components.GlassCard
import app.aura.ui.scene.WeatherScene
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType

@Composable
fun NowScreen(
    state: WeatherUiState,
    onOpenCities: () -> Unit,
    onSceneTap: () -> Unit,
    contentPadding: PaddingValues,
) {
    val forecast = state.forecast
    val scene = state.scene
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(contentPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AuraIcons.Pin, contentDescription = null, tint = AuraColors.TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(state.city.name, style = AuraType.Condition, color = AuraColors.TextPrimary)
                }
                Text("Сейчас · ${Formatters.time(state.localTime.toLocalTime())}", style = AuraType.Caption, color = AuraColors.TextSecondary)
            }
            IconButton(
                onClick = onOpenCities,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AuraColors.GlassFill)
                    .border(1.dp, AuraColors.GlassStroke, CircleShape),
            ) {
                Icon(AuraIcons.Plus, contentDescription = "Города", tint = AuraColors.TextPrimary)
            }
        }

        if (scene != null) {
            WeatherScene(
                scene = scene,
                modifier = Modifier.fillMaxWidth().height(300.dp).padding(top = 8.dp),
                onTap = onSceneTap,
            )
        } else {
            Spacer(Modifier.height(308.dp))
        }

        if (forecast != null && scene != null) {
            val today = forecast.daily.first()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Formatters.temperature(forecast.current.temperatureC), style = AuraType.Temperature, color = AuraColors.TextPrimary)
                Text(Formatters.condition(scene.condition, scene.isNight), style = AuraType.Condition, color = AuraColors.TextPrimary)
                Text(
                    "Ощущается как ${Formatters.temperature(forecast.current.feelsLikeC)} · ↑${Formatters.temperature(today.maxC)} ↓${Formatters.temperature(today.minC)}",
                    style = AuraType.Body,
                    color = AuraColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                if (state.demoCondition != null) {
                    Text(
                        "Демо-режим: погода на сцене меняется по нажатию",
                        style = AuraType.Caption,
                        color = AuraColors.Sun,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            state.nowcast?.let { nowcast ->
                GlassCard(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(AuraColors.Ice.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AuraIcons.Drop, contentDescription = null, tint = Color(0xFF9FEFFF), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(nowcast.headline, style = AuraType.CardTitle, color = AuraColors.TextPrimary)
                            Text("Уверенность прогноза ${forecast.nowcast.confidencePercent} %", style = AuraType.Caption, color = AuraColors.TextSecondary)
                        }
                    }
                }
            }

            HourlyStrip(state, Modifier.padding(top = 12.dp))
            state.sunTimes?.let { sun ->
                Text(
                    "Восход ${sun.sunrise?.let(Formatters::time) ?: "—"} · закат ${sun.sunset?.let(Formatters::time) ?: "—"}",
                    style = AuraType.Caption,
                    color = AuraColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HourlyStrip(state: WeatherUiState, modifier: Modifier = Modifier) {
    val forecast = state.forecast ?: return
    var selected by remember { mutableIntStateOf(0) }
    GlassCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(forecast.hourly) { index, hour ->
                val label = if (index == 0) "Сейчас" else Formatters.time(hour.time)
                val night = hour.time.hour < 7 || hour.time.hour >= 18
                Column(
                    modifier = Modifier
                        .width(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (index == selected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                        .selectable(selected = index == selected, role = Role.Tab, onClick = { selected = index })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(label, style = AuraType.Caption, color = AuraColors.TextSecondary)
                    ConditionOrb(if (index == 0) state.scene?.condition ?: hour.condition else hour.condition, isNight = night)
                    Text(Formatters.temperature(hour.temperatureC), style = AuraType.CardTitle, color = AuraColors.TextPrimary)
                    if (hour.precipitationChancePercent >= 30 && hour.condition != Condition.CLEAR) {
                        Text("${hour.precipitationChancePercent} %", style = AuraType.Caption, color = Color(0xFF9FEFFF))
                    }
                }
            }
        }
    }
}
