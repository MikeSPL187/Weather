package app.aura.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.aura.WeatherUiState
import app.aura.WeatherViewModel
import app.aura.ui.cities.CitiesScreen
import app.aura.ui.components.Tab
import app.aura.ui.components.TabBar
import app.aura.ui.forecast.ForecastScreen
import app.aura.ui.now.NowScreen
import app.aura.ui.scene.skyBrush

@Composable
fun AuraApp(viewModel: WeatherViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.NOW) }
    var showCities by rememberSaveable { mutableStateOf(false) }

    if (showCities) {
        BackHandler { showCities = false }
        CitiesScreen(
            selected = state.city,
            onSelect = { city ->
                viewModel.selectCity(city.id)
                showCities = false
            },
            onBack = { showCities = false },
        )
        return
    }

    AuraScaffold(
        state = state,
        tab = tab,
        onTabChange = { tab = it },
        onOpenCities = { showCities = true },
        onSceneTap = viewModel::cycleDemoCondition,
    )
}

/** Sky background, the current tab and the floating tab bar; stateless so screenshots can render it. */
@Composable
fun AuraScaffold(
    state: WeatherUiState,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    onOpenCities: () -> Unit,
    onSceneTap: () -> Unit,
) {
    // Space the floating tab bar occupies, so scrolling content clears it.
    val barPadding = PaddingValues(bottom = 68.dp + 22.dp + 16.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(skyBrush(state.scene)),
    ) {
        when (tab) {
            Tab.NOW -> NowScreen(
                state = state,
                onOpenCities = onOpenCities,
                onSceneTap = onSceneTap,
                contentPadding = barPadding,
            )
            Tab.FORECAST -> ForecastScreen(state, barPadding)
            Tab.MAP -> ComingSoonScreen(
                title = "Карта",
                description = "3D-карта осадков с шагом времени готова в макетах и появится после подключения радарных данных.",
                contentPadding = barPadding,
            )
            Tab.ASSISTANT -> ComingSoonScreen(
                title = "Ассистент",
                description = "Советы «что надеть» и «лучшее окно» для пробежки и прогулки появятся в версии 2.0.",
                contentPadding = barPadding,
            )
        }
        TabBar(
            selected = tab,
            onSelect = onTabChange,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
        )
    }
}
