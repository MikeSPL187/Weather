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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aura.WeatherUiState
import app.aura.core.format.Formatters
import app.aura.core.model.DataSource
import app.aura.ui.components.AuraIcons
import app.aura.ui.components.GlassCard
import app.aura.ui.scene.WeatherScene
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType

@Composable
fun NowScreen(
    state: WeatherUiState,
    onOpenCities: () -> Unit,
    onScenePreview: () -> Unit,
    onEndPreview: () -> Unit,
    onRetry: () -> Unit,
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
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClickLabel = "Выбрать город", onClick = onOpenCities)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
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

        state.error?.let { message ->
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).clickable(onClickLabel = "Повторить", onClick = onRetry),
                fill = Color(0x66402A10),
            ) {
                Text("$message Нажмите, чтобы повторить.", style = AuraType.Caption, color = Color(0xFFFFE0B0))
            }
        }

        if (scene != null) {
            WeatherScene(
                scene = scene,
                modifier = Modifier.fillMaxWidth().height(330.dp),
                onLongPress = onScenePreview,
            )
        } else {
            Box(Modifier.fillMaxWidth().height(330.dp), contentAlignment = Alignment.Center) {
                Text("Загружаем погоду…", style = AuraType.Body, color = AuraColors.TextSecondary)
            }
        }

        if (forecast != null && scene != null) {
            val today = forecast.daily.firstOrNull()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Formatters.temperature(forecast.current.temperatureC), style = AuraType.Temperature, color = AuraColors.TextPrimary)
                Text(Formatters.condition(forecast.current.condition, scene.isNight), style = AuraType.Condition, color = AuraColors.TextPrimary)
                Text(
                    buildString {
                        append("Ощущается как ${Formatters.temperature(forecast.current.feelsLikeC)}")
                        if (today != null) append(" · ↑${Formatters.temperature(today.maxC)} ↓${Formatters.temperature(today.minC)}")
                    },
                    style = AuraType.Body,
                    color = AuraColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                state.preview?.let { preview ->
                    Text(
                        "Предпросмотр сцены: ${Formatters.condition(preview, scene.isNight).lowercase()} · вернуть",
                        style = AuraType.Caption,
                        color = AuraColors.Sun,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = onEndPreview)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            state.nowcast?.let { nowcast ->
                GlassCard(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                Text("Вероятность осадков ${forecast.nowcast.precipitationChancePercent} %", style = AuraType.Caption, color = AuraColors.TextSecondary)
                            }
                        }
                        NowcastChart(forecast.nowcast)
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("24 ЧАСА", style = AuraType.Label, color = AuraColors.TextSecondary)
                    HourlyChart(forecast.hourly)
                }
            }

            DetailCards(state, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))

            Text(
                when (forecast.source) {
                    DataSource.LIVE -> "Данные: Open-Meteo (CC BY 4.0)${state.updatedAt?.let { " · обновлено ${Formatters.time(it)}" } ?: ""}"
                    DataSource.SAMPLE -> "Пример данных: реальный прогноз не загружен"
                },
                style = AuraType.Caption,
                color = AuraColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            )
        }
    }
}
