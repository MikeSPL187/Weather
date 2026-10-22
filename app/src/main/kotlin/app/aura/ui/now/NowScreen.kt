package app.aura.ui.now

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowScreen(
    state: WeatherUiState,
    onOpenCities: () -> Unit,
    onScenePreview: () -> Unit,
    onEndPreview: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit = {},
    contentPadding: PaddingValues,
) {
    val forecast = state.forecast
    val scene = state.scene
    val scroll = rememberScrollState()
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
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

            state.error?.let { message -> ErrorBanner(message, onRetry, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) }

            if (scene != null) {
                // The scene drifts slower than the page and fades as cards scroll over it.
                WeatherScene(
                    scene = scene,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .graphicsLayer {
                            translationY = scroll.value * 0.45f
                            alpha = 1f - (scroll.value / 1100f).coerceIn(0f, 0.6f)
                        },
                    onLongPress = onScenePreview,
                )
            } else {
                LoadingSkeleton()
            }

            if (forecast != null && scene != null) {
                val today = forecast.daily.firstOrNull()
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedContent(
                        targetState = Formatters.temperature(forecast.current.temperatureC),
                        transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith (fadeOut() + slideOutVertically { -it / 3 }) },
                        label = "temperature",
                    ) { value ->
                        Text(value, style = AuraType.Temperature, color = AuraColors.TextPrimary)
                    }
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
                        HourlyChart(forecast.hourly, state.sunTimes?.sunrise, state.sunTimes?.sunset)
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
}

@Composable
internal fun ErrorBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC2A1A08))
            .border(1.dp, Color(0x66FFB547), RoundedCornerShape(18.dp))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(AuraColors.Sun))
        Spacer(Modifier.width(10.dp))
        Text(message, style = AuraType.Caption, color = Color(0xFFFFE0B0), modifier = Modifier.weight(1f))
        Text(
            "Повторить",
            style = AuraType.CardTitle,
            color = AuraColors.OnSun,
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AuraColors.Sun)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** Placeholder shapes in the layout of the real screen while the first forecast loads. */
@Composable
private fun LoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(0.06f, 0.16f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val block = Color.White.copy(alpha = pulse)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = "Загружаем погоду" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(top = 40.dp).size(220.dp, 150.dp).clip(RoundedCornerShape(80.dp)).background(block))
        Spacer(Modifier.height(30.dp))
        Box(Modifier.size(150.dp, 86.dp).clip(RoundedCornerShape(24.dp)).background(block))
        Box(Modifier.size(200.dp, 18.dp).clip(RoundedCornerShape(9.dp)).background(block))
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(22.dp)).background(block))
        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(22.dp)).background(block))
    }
}
