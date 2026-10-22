package app.aura.ui.tips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.aura.WeatherUiState
import app.aura.core.advice.ActivityWindow
import app.aura.core.advice.Garment
import app.aura.core.format.Formatters
import app.aura.ui.components.AuraIcons
import app.aura.ui.components.GlassCard
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TipsScreen(state: WeatherUiState, contentPadding: PaddingValues) {
    var selected by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AuraColors.Sun.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AuraIcons.Bulb, contentDescription = null, tint = AuraColors.Sun, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Советы", style = AuraType.Title, color = AuraColors.TextPrimary)
                Text("Что надеть и когда выйти · ${state.city.name}", style = AuraType.Caption, color = AuraColors.TextSecondary)
            }
        }

        val outfit = state.outfit
        if (outfit == null) {
            Text("Советы появятся, когда загрузится прогноз.", style = AuraType.Body, color = AuraColors.TextSecondary)
        } else {

            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ЧТО НАДЕТЬ · СЛЕДУЮЩИЕ 10 ЧАСОВ", style = AuraType.Label, color = AuraColors.TextSecondary)
                    Text(outfit.summary, style = AuraType.Body, color = AuraColors.TextPrimary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        outfit.items.forEach { item ->
                            val accent = when (item.kind) {
                                Garment.OUTERWEAR -> AuraColors.Sun
                                Garment.LAYER -> Color(0xFFB6F09C)
                                Garment.BOTTOM -> AuraColors.Ice
                                Garment.SHOES -> Color(0xFFD7C4FF)
                                Garment.ACCESSORY -> Color(0xFFFFD58F)
                            }
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                                Spacer(Modifier.width(8.dp))
                                Text(item.label, style = AuraType.CardTitle, color = AuraColors.TextPrimary)
                            }
                        }
                    }
                }
            }

            if (state.activities.isNotEmpty()) {
                val index = selected.coerceIn(0, state.activities.lastIndex)
                val window = state.activities[index]
                GlassCard(Modifier.fillMaxWidth(), fill = Color(0x26FFB547)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ЛУЧШЕЕ ВРЕМЯ", style = AuraType.Label, color = Color(0xFFFFD58F))
                        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.activities.forEachIndexed { i, a ->
                                val on = i == index
                                Text(
                                    a.activity.label,
                                    style = AuraType.Caption,
                                    color = if (on) AuraColors.Ink else AuraColors.TextPrimary,
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (on) Color.White else Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                        .selectable(selected = on, role = Role.Tab, onClick = { selected = i })
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${Formatters.time(window.start.toLocalTime())}–${Formatters.time(window.end.toLocalTime())}",
                                style = AuraType.Title,
                                color = AuraColors.TextPrimary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text("${window.score}/10", style = AuraType.CardTitle, color = Color(0xFFFFD58F))
                        }
                        if (window.start.toLocalDate() != state.localTime.toLocalDate()) {
                            Text("Завтра", style = AuraType.Caption, color = AuraColors.TextSecondary)
                        }
                        ScoreBars(window)
                        Text(window.reason, style = AuraType.Body, color = AuraColors.TextSecondary)
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AuraIcons.Bulb, contentDescription = null, tint = AuraColors.Sun, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Советы считаются прямо на телефоне по понятным правилам из почасового прогноза: температура по ощущениям, осадки, ветер, УФ и время восхода и заката. Без внешних сервисов и без ИИ.",
                        style = AuraType.Caption,
                        color = AuraColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreBars(window: ActivityWindow) {
    val scores = window.hourly
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { contentDescription = "Пригодность погоды по часам, лучшее окно выделено" },
        ) {
            val gap = 3.dp.toPx()
            val barW = (size.width - gap * (scores.size - 1)) / scores.size
            scores.forEachIndexed { i, s ->
                val inWindow = !s.time.isBefore(window.start) && s.time.isBefore(window.end)
                val hgt = (size.height * (0.12f + 0.88f * s.score / 10f))
                drawRoundRect(
                    if (inWindow) Brush.verticalGradient(listOf(Color(0xFFFFE3A0), AuraColors.Sun))
                    else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.16f))),
                    topLeft = Offset(i * (barW + gap), size.height - hgt),
                    size = Size(barW, hgt),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, scores.size / 2, scores.lastIndex).forEach { i ->
                Text(Formatters.time(scores[i].time.toLocalTime()), style = AuraType.Caption, color = AuraColors.TextSecondary)
            }
        }
    }
}
