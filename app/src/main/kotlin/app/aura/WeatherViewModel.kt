package app.aura

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aura.core.data.Cities
import app.aura.core.data.SampleWeatherRepository
import app.aura.core.data.WeatherRepository
import app.aura.core.model.City
import app.aura.core.model.Condition
import app.aura.core.model.Forecast
import app.aura.core.nowcast.NowcastSummarizer
import app.aura.core.nowcast.NowcastSummary
import app.aura.core.solar.SolarCalculator
import app.aura.core.solar.SolarPosition
import app.aura.core.solar.SunTimes
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Everything the scene needs to draw the sky: weather plus where the sun really is. */
data class SceneState(
    val condition: Condition,
    val sun: SolarPosition,
    val landmarkCity: City,
) {
    val isNight: Boolean get() = sun.elevationDeg < -2
}

data class WeatherUiState(
    val city: City = Cities.moscow,
    val localTime: LocalDateTime = LocalDateTime.now(),
    val forecast: Forecast? = null,
    val sunTimes: SunTimes? = null,
    val nowcast: NowcastSummary? = null,
    val scene: SceneState? = null,
    /** Set when the viewer cycles the weather by tapping the scene; sample data only. */
    val demoCondition: Condition? = null,
)

class WeatherViewModel(
    private val repository: WeatherRepository = SampleWeatherRepository(),
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val _state = MutableStateFlow(WeatherUiState())
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        selectCity(Cities.moscow.id)
    }

    fun selectCity(id: String) {
        val city = Cities.byId(id) ?: return
        _state.update { it.copy(city = city, forecast = null) }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                refresh(city)
                delay(REFRESH_MS)
            }
        }
    }

    /** Cycles the scene through every condition so the 3D states can be reviewed on sample data. */
    fun cycleDemoCondition() {
        _state.update { current ->
            val base = current.demoCondition ?: current.forecast?.current?.condition ?: Condition.CLEAR
            val next = Condition.entries[(base.ordinal + 1) % Condition.entries.size]
            current.copy(demoCondition = next, scene = current.scene?.copy(condition = next))
        }
    }

    private suspend fun refresh(city: City) {
        val now = LocalDateTime.ofInstant(clock(), ZoneOffset.ofHours(city.utcOffsetHours))
        val forecast = repository.forecast(city, now)
        val minutes = now.hour * 60 + now.minute + now.second / 60.0
        val sun = SolarCalculator.position(city, now.toLocalDate(), minutes)
        _state.update { current ->
            val condition = current.demoCondition ?: forecast.current.condition
            current.copy(
                city = city,
                localTime = now,
                forecast = forecast,
                sunTimes = SolarCalculator.sunTimes(city, now.toLocalDate()),
                nowcast = NowcastSummarizer.summarize(forecast.nowcast),
                scene = SceneState(condition, sun, city),
            )
        }
    }

    private companion object {
        const val REFRESH_MS = 30_000L
    }
}
