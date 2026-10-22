package app.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType

enum class Tab(val label: String, val icon: ImageVector) {
    NOW("Сейчас", AuraIcons.Home),
    FORECAST("Прогноз", AuraIcons.Calendar),
    MAP("Карта", AuraIcons.Layers),
    TIPS("Советы", AuraIcons.Bulb),
}

/** Floating glass pill with four tabs, as in the mockups. */
@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(34.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .background(Color(0xD9080E26))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val color = if (isSelected) AuraColors.TextPrimary else Color.White.copy(alpha = 0.8f)
                Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Text(tab.label, style = AuraType.Caption, color = color, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
