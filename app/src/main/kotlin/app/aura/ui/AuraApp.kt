package app.aura.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import app.aura.ui.scene.skyColors
import app.aura.ui.tips.TipsScreen

@Composable
fun AuraApp(viewModel: WeatherViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.NOW) }
    var showCities by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    if (showCities) {
        BackHandler { showCities = false }
        CitiesScreen(
            state = state,
            onSelect = { city ->
                viewModel.select(city)
                showCities = false
            },
            onAdd = { city ->
                viewModel.addCity(city)
                showCities = false
            },
            onRemove = { viewModel.removeCity(it.id) },
            onQueryChange = viewModel::search,
            onBack = { showCities = false },
        )
        return
    }

    AuraScaffold(
        state = state,
        tab = tab,
        onTabChange = {
            if (it != tab) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            tab = it
        },
        onOpenCities = { showCities = true },
        onScenePreview = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.cyclePreview()
        },
        onEndPreview = viewModel::endPreview,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
    )
}

/** Sky background, the current tab and the floating tab bar; stateless so screenshots can render it. */
@Composable
fun AuraScaffold(
    state: WeatherUiState,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    onOpenCities: () -> Unit,
    onScenePreview: () -> Unit = {},
    onEndPreview: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    // The sky fades between cities, times of day and weather instead of jumping.
    val (skyTop, skyBottom) = state.scene?.let(::skyColors) ?: (Color(0xFF0A1030) to Color(0xFF15205A))
    val top by animateColorAsState(skyTop, tween(900), label = "skyTop")
    val bottom by animateColorAsState(skyBottom, tween(900), label = "skyBottom")
    // Space the floating tab bar occupies, so scrolling content clears it.
    val barPadding = PaddingValues(bottom = 68.dp + 22.dp + 16.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, lerp(top, bottom, 0.55f), bottom))),
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "tab",
        ) { current ->
            when (current) {
                Tab.NOW -> NowScreen(
                    state = state,
                    onOpenCities = onOpenCities,
                    onScenePreview = onScenePreview,
                    onEndPreview = onEndPreview,
                    onRetry = onRetry,
                    onRefresh = onRefresh,
                    contentPadding = barPadding,
                )
                Tab.FORECAST -> ForecastScreen(state, barPadding)
                Tab.MAP -> ComingSoonScreen(
                    title = "Карта",
                    description = "3D-карта осадков с шагом времени готова в макетах и появится после подключения радарных данных.",
                    contentPadding = barPadding,
                )
                Tab.TIPS -> TipsScreen(state, barPadding)
            }
        }
        // Content fades out under the tab bar instead of being cut by it.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, bottom.copy(alpha = 0.92f)))),
        )
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
