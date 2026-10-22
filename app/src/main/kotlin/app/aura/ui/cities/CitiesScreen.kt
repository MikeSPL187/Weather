package app.aura.ui.cities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.aura.core.data.Cities
import app.aura.core.model.City
import app.aura.ui.components.AuraIcons
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType

@Composable
fun CitiesScreen(selected: City, onSelect: (City) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AuraColors.Ink, Color(0xFF15205A))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(AuraIcons.Back, contentDescription = "Назад", tint = AuraColors.TextPrimary)
            }
            Text("Города", style = AuraType.Title, color = AuraColors.TextPrimary)
        }
        Cities.all.forEach { city ->
            val isSelected = city.id == selected.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF1D4ED8), Color(0xFF4F8EF7))))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(city) })
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(city.name, style = AuraType.Condition, color = AuraColors.TextPrimary)
                    Text("UTC+${city.utcOffsetHours}", style = AuraType.Caption, color = AuraColors.TextSecondary)
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AuraIcons.Check, contentDescription = "Выбран", tint = AuraColors.TextPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Поиск городов и районов появится вместе с подключением реального прогноза.",
            style = AuraType.Caption,
            color = AuraColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.width(1.dp))
    }
}
