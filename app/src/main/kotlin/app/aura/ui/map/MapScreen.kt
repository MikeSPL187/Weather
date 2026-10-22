package app.aura.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aura.WeatherUiState
import app.aura.core.format.Formatters
import app.aura.core.map.MapLayer
import app.aura.core.map.PrecipitationRadar
import app.aura.core.map.RegionGrid
import app.aura.core.model.DataSource
import app.aura.ui.components.GlassCard
import app.aura.ui.now.ErrorBanner
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType
import kotlin.math.roundToInt

private val MapLayer.label: String
    get() = when (this) {
        MapLayer.PRECIPITATION -> "Осадки"
        MapLayer.CLOUDS -> "Облака"
        MapLayer.TEMPERATURE -> "Темп."
        MapLayer.WIND -> "Ветер"
    }

/** One sentence about the city at the chosen hour for the chosen layer. */
private fun summary(grid: RegionGrid, layer: MapLayer, hour: Float): String {
    val mid = (grid.size - 1) / 2f
    fun here(frames: List<DoubleArray>) = grid.sample(frames, hour, mid, mid)
    return when (layer) {
        MapLayer.PRECIPITATION -> PrecipitationRadar.describe(grid, hour.roundToInt())
        MapLayer.CLOUDS -> "Облачность над вами ${here(grid.cloudCoverPercent).roundToInt()} %"
        MapLayer.TEMPERATURE -> {
            val frame = grid.temperatureC[hour.roundToInt().coerceIn(0, grid.times.lastIndex)]
            "У вас ${Formatters.temperature(here(grid.temperatureC))}, в округе от ${Formatters.temperature(frame.min())} до ${Formatters.temperature(frame.max())}"
        }
        MapLayer.WIND -> {
            val direction = grid.windDirectionDeg[hour.roundToInt().coerceIn(0, grid.times.lastIndex)][grid.size * grid.size / 2]
            "Ветер у вас ${here(grid.windSpeedMs).roundToInt()} м/с, ${Formatters.windName(direction.roundToInt())}"
        }
    }
}

@Composable
fun MapScreen(state: WeatherUiState, onRetry: () -> Unit, contentPadding: PaddingValues, initialLayer: MapLayer = MapLayer.PRECIPITATION) {
    var layer by rememberSaveable { mutableStateOf(initialLayer) }
    val grid = state.regionGrid
    var hour by remember(grid) { mutableFloatStateOf(0f) }
    var playing by remember(grid) { mutableStateOf(false) }
    if (playing && grid != null) {
        LaunchedEffect(grid) {
            var last = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                // Two hours of forecast per second, then back to now.
                hour += (now - last) / 1_000_000_000f * 2f
                if (hour > grid.times.lastIndex) hour = 0f
                last = now
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text("Карта", style = AuraType.Title, color = AuraColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "${state.city.name} · ${grid?.radiusKm?.roundToInt() ?: 120} км вокруг",
                style = AuraType.Caption,
                color = AuraColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        LayerPicker(layer, onPick = { layer = it })

        state.mapError?.let { ErrorBanner(it, onRetry) }

        Box(Modifier.fillMaxWidth().height(350.dp), contentAlignment = Alignment.Center) {
            if (grid == null) {
                Text("Загружаем карту…", style = AuraType.Body, color = AuraColors.TextSecondary)
            } else {
                val temperature = state.forecast?.current?.temperatureC ?: grid.sample(grid.temperatureC, 0f, (grid.size - 1) / 2f, (grid.size - 1) / 2f)
                RegionMap(
                    grid = grid,
                    layer = layer,
                    hour = hour,
                    hereLabel = "Вы здесь · ${Formatters.temperature(temperature)}",
                    description = "Карта: ${layer.label.lowercase()}. ${summary(grid, layer, hour)}. Проведите в сторону, чтобы повернуть",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (grid != null) {
            Legend(layer)
            TimeCard(grid, layer, hour, playing, onHour = { playing = false; hour = it }, onPlay = { playing = !playing })
            Text(
                if (grid.source == DataSource.SAMPLE) "Пример данных: нет связи с сервером прогноза."
                else "Прогноз модели Open-Meteo (CC BY 4.0) по сетке ${grid.size}×${grid.size} с шагом ${grid.stepKm.roundToInt()} км. Это не радар: мелкие ливни карта может не показать.",
                style = AuraType.Caption,
                color = AuraColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun LayerPicker(selected: MapLayer, onPick: (MapLayer) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AuraColors.GlassFill)
            .border(1.dp, AuraColors.GlassStroke, RoundedCornerShape(22.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MapLayer.entries.forEach { layer ->
            val on = layer == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onPick(layer) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(layer.label, style = AuraType.CardTitle, color = if (on) AuraColors.Ink else AuraColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun Legend(layer: MapLayer) {
    val (stops, labels) = when (layer) {
        MapLayer.PRECIPITATION -> PrecipitationStops to listOf("Слабые", "1 мм/ч", "Ливень")
        MapLayer.CLOUDS -> CloudStops.drop(1) to listOf("Ясно", "50 %", "Сплошная")
        MapLayer.TEMPERATURE -> TemperatureStops to listOf("−25°", "0°", "+30°")
        MapLayer.WIND -> WindStops to listOf("Штиль", "10 м/с", "Шторм")
    }
    Column(Modifier.padding(horizontal = 4.dp).semantics(mergeDescendants = true) {}) {
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val colors = stops.map { it.second.copy(alpha = 1f) }
            drawRoundRect(Brush.horizontalGradient(colors), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEachIndexed { i, text ->
                Text(
                    text,
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

@Composable
private fun TimeCard(grid: RegionGrid, layer: MapLayer, hour: Float, playing: Boolean, onHour: (Float) -> Unit, onPlay: () -> Unit) {
    val step = hour.roundToInt().coerceIn(0, grid.times.lastIndex)
    val time = Formatters.time(grid.times[step].toLocalTime())
    GlassCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AuraColors.Sun)
                        .semantics { contentDescription = if (playing) "Пауза" else "Показать следующие часы" },
                ) {
                    PlayPauseGlyph(playing)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (step == 0) "Сейчас · $time" else "Через $step ч · $time", style = AuraType.CardTitle, color = AuraColors.TextPrimary)
                    Text(summary(grid, layer, hour), style = AuraType.Body, color = AuraColors.TextSecondary)
                }
            }
            Slider(
                value = hour,
                onValueChange = onHour,
                valueRange = 0f..grid.times.lastIndex.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = AuraColors.Sun,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                ),
                modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = "Время прогноза" },
            )
            Row(Modifier.fillMaxWidth()) {
                listOf(0, grid.times.lastIndex / 2, grid.times.lastIndex).forEachIndexed { i, index ->
                    Text(
                        if (index == 0) "Сейчас" else Formatters.time(grid.times[index].toLocalTime()),
                        style = AuraType.Caption,
                        color = AuraColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                        textAlign = when (i) {
                            0 -> TextAlign.Start
                            2 -> TextAlign.End
                            else -> TextAlign.Center
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayPauseGlyph(playing: Boolean) {
    Canvas(Modifier.size(18.dp)) {
        val ink = AuraColors.OnSun
        if (playing) {
            val w = size.width * 0.28f
            drawRect(ink, topLeft = Offset(size.width * 0.14f, 0f), size = Size(w, size.height))
            drawRect(ink, topLeft = Offset(size.width * 0.58f, 0f), size = Size(w, size.height))
        } else {
            drawPath(Path().apply {
                moveTo(size.width * 0.2f, 0f)
                lineTo(size.width, size.height / 2)
                lineTo(size.width * 0.2f, size.height)
                close()
            }, ink)
        }
    }
}
