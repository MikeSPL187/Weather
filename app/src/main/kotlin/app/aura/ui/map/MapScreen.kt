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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
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
import app.aura.core.map.MapPalette
import app.aura.core.map.MapTimeline
import app.aura.core.map.PrecipitationRadar
import app.aura.core.map.RegionGrid
import app.aura.core.model.DataSource
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

private val Panel = Color(0xE60A1030)

/** One sentence about the city at the chosen moment for the chosen layer. */
private fun summary(grid: RegionGrid, timeline: MapTimeline, layer: MapLayer, position: Float): String {
    val hour = timeline.moment(position).forecastHour
    val step = timeline.forecastStep(position)
    val mid = (grid.size - 1) / 2f
    fun here(frames: List<DoubleArray>) = grid.sample(frames, hour, mid, mid)
    return when (layer) {
        MapLayer.PRECIPITATION ->
            if (timeline.isRadar(position)) "Радар: что выпало на самом деле. Дальше — прогноз на 12 часов"
            else PrecipitationRadar.describe(grid, step)
        MapLayer.CLOUDS -> "Облачность над вами ${here(grid.cloudCoverPercent).roundToInt()} %"
        MapLayer.TEMPERATURE -> {
            val frame = grid.temperatureC[step]
            "У вас ${Formatters.temperature(here(grid.temperatureC))}, вокруг от ${Formatters.temperature(frame.min())} до ${Formatters.temperature(frame.max())}"
        }
        MapLayer.WIND -> {
            val direction = grid.windDirectionDeg[step][grid.size * grid.size / 2]
            "Ветер у вас ${here(grid.windSpeedMs).roundToInt()} м/с, ${Formatters.windName(direction.roundToInt())}"
        }
    }
}

@Composable
fun MapScreen(state: WeatherUiState, onRetry: () -> Unit, contentPadding: PaddingValues, initialLayer: MapLayer = MapLayer.PRECIPITATION) {
    var layer by rememberSaveable { mutableStateOf(initialLayer) }
    var view by rememberSaveable { mutableStateOf(MapView3d.REGION) }
    val grid = state.regionGrid
    val radar = state.radar?.takeIf { layer == MapLayer.PRECIPITATION }
    val timeline = remember(grid, radar, state.city.id) {
        grid?.let { MapTimeline(radar?.past.orEmpty(), it.times, state.city.utcOffsetMinutes) }
    }
    var position by remember(timeline) { mutableFloatStateOf(timeline?.now?.toFloat() ?: 0f) }
    var playing by remember(timeline) { mutableStateOf(false) }
    if (playing && timeline != null) {
        LaunchedEffect(timeline) {
            var last = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                // About a frame and a half per second, then back to the start.
                position += (now - last) / 1_000_000_000f * 1.5f
                if (position > timeline.last) position = 0f
                last = now
            }
        }
    }
    val temperature = state.forecast?.current?.temperatureC

    Box(Modifier.fillMaxSize()) {
        WeatherMap(
            city = state.city,
            grid = grid,
            radar = radar,
            timeline = timeline,
            layer = layer,
            position = position,
            view = view,
            hereLabel = state.city.name + (temperature?.let { " · " + Formatters.temperature(it) } ?: ""),
            modifier = Modifier.fillMaxSize(),
        )

        // Top: title and layers over a scrim so they read on any part of the map.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xF00A1030), Color(0x990A1030), Color.Transparent)))
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Карта", style = AuraType.Title, color = AuraColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.city.name} · ${grid?.radiusKm?.roundToInt() ?: 125} км вокруг",
                    style = AuraType.Caption,
                    color = AuraColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            LayerPicker(layer, onPick = { layer = it })
            state.mapError?.let { ErrorBanner(it, onRetry) }
        }

        // Right: back to the region, or down into the city in 3D.
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapButton(if (view == MapView3d.CITY) "Регион" else "3D", if (view == MapView3d.CITY) "Показать весь регион" else "Показать город в 3D") {
                view = if (view == MapView3d.CITY) MapView3d.REGION else MapView3d.CITY
            }
        }

        // Bottom: the timeline, the legend and what it means for the city.
        if (grid != null && timeline != null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(contentPadding)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Panel)
                    .border(1.dp, AuraColors.GlassStroke, RoundedCornerShape(26.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                TimeControls(grid, timeline, layer, position, playing, onPosition = { playing = false; position = it }, onPlay = { playing = !playing })
                Spacer(Modifier.height(10.dp))
                Legend(layer)
                Text(
                    sourceNote(state, layer),
                    style = AuraType.Caption.copy(fontSize = AuraType.Caption.fontSize * 0.9f),
                    color = AuraColors.TextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else if (grid == null) {
            Text(
                "Загружаем карту…",
                style = AuraType.Body,
                color = AuraColors.TextSecondary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun sourceNote(state: WeatherUiState, layer: MapLayer): String = when {
    state.regionGrid?.source == DataSource.SAMPLE -> "Пример данных: нет связи с сервером прогноза."
    layer == MapLayer.PRECIPITATION && state.radar != null -> "Радар RainViewer · прогноз Open-Meteo · карта © OpenStreetMap"
    else -> "Прогноз Open-Meteo по сетке 25 км · карта © OpenStreetMap"
}

@Composable
private fun MapButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Panel)
            .border(1.dp, AuraColors.GlassStroke, CircleShape)
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AuraType.CardTitle, color = AuraColors.TextPrimary)
    }
}

@Composable
private fun LayerPicker(selected: MapLayer, onPick: (MapLayer) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Panel)
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
    val labels = when (layer) {
        MapLayer.PRECIPITATION -> listOf("Слабые", "Умеренные", "Ливень")
        MapLayer.CLOUDS -> listOf("Ясно", "50 %", "Сплошная")
        MapLayer.TEMPERATURE -> listOf("−25°", "0°", "+30°")
        MapLayer.WIND -> listOf("Штиль", "10 м/с", "Шторм")
    }
    val colors = MapPalette.stops(layer).drop(if (layer == MapLayer.WIND || layer == MapLayer.PRECIPITATION) 1 else 0).map { Color(it.second.toInt()).copy(alpha = 1f) }
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawRoundRect(Brush.horizontalGradient(colors), cornerRadius = CornerRadius(size.height / 2))
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
private fun TimeControls(
    grid: RegionGrid,
    timeline: MapTimeline,
    layer: MapLayer,
    position: Float,
    playing: Boolean,
    onPosition: (Float) -> Unit,
    onPlay: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(AuraColors.Sun)
                    .semantics { contentDescription = if (playing) "Пауза" else "Проиграть" },
            ) {
                PlayPauseGlyph(playing)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(timeline.label(position), style = AuraType.CardTitle, color = AuraColors.TextPrimary)
                    if (timeline.isRadar(position)) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "РАДАР",
                            style = AuraType.Label,
                            color = AuraColors.Ink,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AuraColors.Ice).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(summary(grid, timeline, layer, position), style = AuraType.Body, color = AuraColors.TextSecondary, maxLines = 2)
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            // The observed part of the timeline is tinted, so radar and forecast read apart.
            if (timeline.now > 0) {
                Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                    val inset = 10.dp.toPx()
                    val w = size.width - inset * 2
                    val x = inset + w * timeline.now / timeline.last.coerceAtLeast(1)
                    drawRoundRect(AuraColors.Ice.copy(alpha = 0.35f), topLeft = Offset(inset, size.height / 2 - 3.dp.toPx()), size = Size(x - inset, 6.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx()))
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(x, size.height / 2 - 9.dp.toPx()), Offset(x, size.height / 2 + 9.dp.toPx()), strokeWidth = 2.dp.toPx())
                }
            }
            Slider(
                value = position,
                onValueChange = onPosition,
                valueRange = 0f..timeline.last.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = AuraColors.Sun.copy(alpha = 0.85f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                ),
                modifier = Modifier.semantics { contentDescription = "Время на карте" },
            )
        }
        Row(Modifier.fillMaxWidth()) {
            val marks = buildList {
                if (timeline.now > 0) add(0 to "−2 ч")
                add(timeline.now to "Сейчас")
                add(timeline.last to "+${timeline.last - timeline.now} ч")
            }
            marks.forEachIndexed { i, (_, text) ->
                Text(
                    text,
                    style = AuraType.Caption,
                    color = AuraColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        marks.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                )
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

