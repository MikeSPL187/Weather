package app.aura.ui.cities

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.aura.CityWeather
import app.aura.WeatherUiState
import app.aura.core.format.Formatters
import app.aura.core.model.City
import app.aura.core.model.Condition
import app.aura.ui.components.AuraIcons
import app.aura.ui.components.WeatherIcon
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType

private fun cardBrush(weather: CityWeather?): Brush {
    val colors = when {
        weather == null -> listOf(Color(0xFF26325A), Color(0xFF3B4A7A))
        weather.isNight -> listOf(Color(0xFF0B1033), Color(0xFF2B2F6B))
        weather.condition == Condition.RAIN || weather.condition == Condition.STORM -> listOf(Color(0xFF1B2333), Color(0xFF45546F))
        weather.condition == Condition.SNOW -> listOf(Color(0xFF26324B), Color(0xFF7489AE))
        weather.condition == Condition.CLOUDY || weather.condition == Condition.FOG -> listOf(Color(0xFF34466E), Color(0xFF6D84AE))
        else -> listOf(Color(0xFF1D4ED8), Color(0xFF4F8EF7))
    }
    return Brush.horizontalGradient(colors)
}

@Composable
fun CitiesScreen(
    state: WeatherUiState,
    onSelect: (City) -> Unit,
    onAdd: (City) -> Unit,
    onRemove: (City) -> Unit,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AuraColors.Ink, Color(0xFF15205A))))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(AuraIcons.Back, contentDescription = "Назад", tint = AuraColors.TextPrimary)
            }
            Text("Города", style = AuraType.Title, color = AuraColors.TextPrimary)
        }

        SearchField(state.searchQuery, onQueryChange, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.searchQuery.trim().length >= 2) {
                when {
                    state.searching -> item { Hint("Ищем «${state.searchQuery.trim()}»…") }
                    state.searchResults.isEmpty() -> item { Hint("Ничего не нашли. Проверьте название или подключение к сети.") }
                    else -> items(state.searchResults, key = { "r-" + it.id }) { city ->
                        SearchResult(city, onClick = { onAdd(city) })
                    }
                }
            } else {
                items(state.cities, key = { it.id }) { city ->
                    CityCard(
                        city = city,
                        weather = state.cityWeather[city.id],
                        selected = city.id == state.city.id,
                        canRemove = state.cities.size > 1,
                        onSelect = { onSelect(city) },
                        onRemove = { onRemove(city) },
                    )
                }
                item { Hint("Найдите любой город или посёлок: прогноз и сцена появятся для него сразу. Первый город в списке показывает виджет на главном экране.") }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (query.isEmpty()) Text("Город, район или посёлок", style = AuraType.Body, color = AuraColors.TextSecondary)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = AuraType.Body.copy(color = AuraColors.TextPrimary),
                cursorBrush = SolidColor(AuraColors.Sun),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Поиск города" },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(40.dp)) {
                Text("✕", style = AuraType.CardTitle, color = AuraColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = AuraType.Caption, color = AuraColors.TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
}

@Composable
private fun SearchResult(city: City, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(role = Role.Button, onClickLabel = "Добавить город", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(city.name, style = AuraType.CardTitle, color = AuraColors.TextPrimary)
            if (city.region.isNotEmpty()) Text(city.region, style = AuraType.Caption, color = AuraColors.TextSecondary)
        }
        Icon(AuraIcons.Plus, contentDescription = null, tint = AuraColors.Sun, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CityCard(
    city: City,
    weather: CityWeather?,
    selected: Boolean,
    canRemove: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(shape)
            .background(cardBrush(weather))
            .then(if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.7f), shape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = "Показать погоду", onClick = onSelect)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(city.name, style = AuraType.Condition, color = AuraColors.TextPrimary)
            Text(
                weather?.let { Formatters.condition(it.condition, it.isNight) } ?: city.region.ifEmpty { "Загружаем…" },
                style = AuraType.Caption,
                color = AuraColors.TextSecondary,
            )
        }
        if (weather != null) {
            WeatherIcon(weather.condition, isNight = weather.isNight, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(Formatters.temperature(weather.temperatureC), style = AuraType.Title, color = AuraColors.TextPrimary)
        }
        if (canRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) {
                Text("✕", style = AuraType.Caption, color = AuraColors.TextSecondary, modifier = Modifier.semantics { contentDescription = "Удалить ${city.name}" })
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}
