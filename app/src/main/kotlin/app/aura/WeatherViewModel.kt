package app.aura

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.aura.core.advice.Activity
import app.aura.core.advice.ActivityPlanner
import app.aura.core.advice.ActivityWindow
import app.aura.core.advice.Outfit
import app.aura.core.advice.OutfitAdvisor
import app.aura.core.advice.WeekSummary
import app.aura.core.data.Cities
import app.aura.core.data.OpenMeteoWeatherRepository
import app.aura.core.data.SampleWeatherRepository
import app.aura.core.format.Formatters
import app.aura.core.model.City
import app.aura.core.model.Condition
import app.aura.core.model.Forecast
import app.aura.core.nowcast.NowcastSummarizer
import app.aura.core.nowcast.NowcastSummary
import app.aura.core.solar.SolarCalculator
import app.aura.core.solar.SolarPosition
import app.aura.core.solar.SunTimes
import app.aura.data.CityStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
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

/** The short summary each saved city shows in the city list. */
data class CityWeather(val temperatureC: Double, val condition: Condition, val isNight: Boolean)

data class WeatherUiState(
    val cities: List<City> = Cities.defaults,
    val city: City = Cities.moscow,
    val localTime: LocalDateTime = LocalDateTime.now(),
    val forecast: Forecast? = null,
    val sunTimes: SunTimes? = null,
    val nowcast: NowcastSummary? = null,
    val scene: SceneState? = null,
    /** Set while the viewer previews other weather on the scene; the forecast itself is unchanged. */
    val preview: Condition? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val updatedAt: LocalTime? = null,
    val outfit: Outfit? = null,
    val activities: List<ActivityWindow> = emptyList(),
    val weekSummary: String = "",
    val cityWeather: Map<String, CityWeather> = emptyMap(),
    val searchQuery: String = "",
    val searchResults: List<City> = emptyList(),
    val searching: Boolean = false,
)

fun localNow(city: City, instant: Instant = Instant.now()): LocalDateTime =
    LocalDateTime.ofInstant(instant, ZoneOffset.ofTotalSeconds(city.utcOffsetMinutes * 60))

/** Derives everything the screens show from one forecast at the city's local time [now]. */
fun buildUiState(
    forecast: Forecast,
    now: LocalDateTime,
    preview: Condition? = null,
    base: WeatherUiState = WeatherUiState(),
): WeatherUiState {
    val city = forecast.city
    val minutes = now.hour * 60 + now.minute + now.second / 60.0
    val sun = SolarCalculator.position(city, now.toLocalDate(), minutes)
    val today = forecast.daily.firstOrNull()
    val sunTimes = SolarCalculator.sunTimes(city, now.toLocalDate()).let { calc ->
        // Prefer the forecast's own sunrise and sunset when it has them.
        if (today?.sunrise != null && today.sunset != null) calc.copy(sunrise = today.sunrise, sunset = today.sunset) else calc
    }
    val upcoming = forecast.hourly.filter { !it.time.isBefore(now.withMinute(0).withSecond(0).withNano(0)) }.ifEmpty { forecast.hourly }
    return base.copy(
        city = city,
        localTime = now,
        forecast = forecast,
        sunTimes = sunTimes,
        nowcast = NowcastSummarizer.summarize(forecast.nowcast),
        scene = SceneState(preview ?: forecast.current.condition, sun, city),
        preview = preview,
        loading = false,
        outfit = upcoming.take(10).takeIf { it.isNotEmpty() }?.let(OutfitAdvisor::advise),
        activities = Activity.entries.mapNotNull { ActivityPlanner.bestWindow(it, upcoming, sunTimes.sunrise, sunTimes.sunset) },
        weekSummary = WeekSummary.describe(forecast.daily, now.toLocalDate()),
        cityWeather = base.cityWeather + (city.id to CityWeather(forecast.current.temperatureC, forecast.current.condition, sun.elevationDeg < -2)),
    )
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val store = CityStore(application)
    private val live = OpenMeteoWeatherRepository()
    private val sample = SampleWeatherRepository()
    private val cache = mutableMapOf<String, Forecast>()

    private val _state = MutableStateFlow(WeatherUiState())
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        val cities = store.load()
        val selected = cities.firstOrNull { it.id == store.selectedId } ?: cities.first()
        _state.update { it.copy(cities = cities, city = selected) }
        select(selected)
        prefetchCityList(cities)
        viewModelScope.launch {
            // The sun keeps moving between forecast refreshes.
            while (isActive) {
                delay(TICK_MS)
                recompute()
            }
        }
    }

    fun select(city: City) {
        store.selectedId = city.id
        _state.update { current ->
            val cached = cache[city.id]
            if (cached != null) buildUiState(cached, localNow(cached.city), null, current.copy(error = null))
            else current.copy(city = city, forecast = null, scene = null, loading = true, error = null, preview = null)
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            while (isActive) {
                load(city)
                delay(REFRESH_MS)
            }
        }
    }

    fun retry() = select(_state.value.city)

    /** Long press on the scene: cycles through every weather so the 3D states can be previewed. */
    fun cyclePreview() {
        _state.update { current ->
            val base = current.preview ?: current.forecast?.current?.condition ?: Condition.CLEAR
            val next = Condition.entries[(base.ordinal + 1) % Condition.entries.size]
            current.copy(preview = next, scene = current.scene?.copy(condition = next))
        }
    }

    fun endPreview() {
        _state.update { current ->
            current.copy(preview = null, scene = current.forecast?.let { f -> current.scene?.copy(condition = f.current.condition) })
        }
    }

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(searching = true) }
            val results = try {
                live.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { it.copy(searchResults = results, searching = false) }
        }
    }

    fun addCity(city: City) {
        val existing = _state.value.cities.firstOrNull { it.name == city.name && it.region == city.region }
        if (existing == null) {
            val updated = _state.value.cities + city
            store.save(updated)
            _state.update { it.copy(cities = updated) }
        }
        _state.update { it.copy(searchQuery = "", searchResults = emptyList()) }
        select(existing ?: city)
    }

    fun removeCity(id: String) {
        val current = _state.value
        if (current.cities.size <= 1) return
        val updated = current.cities.filterNot { it.id == id }
        store.save(updated)
        cache.remove(id)
        _state.update { it.copy(cities = updated, cityWeather = it.cityWeather - id) }
        if (current.city.id == id) select(updated.first())
    }

    private suspend fun load(city: City) {
        val forecast = try {
            live.forecast(city, localNow(city)).also { cache[city.id] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _state.update { current ->
            if (current.city.id != city.id) return@update current
            when {
                forecast != null -> buildUiState(forecast, localNow(forecast.city), current.preview, current)
                    .copy(error = null, updatedAt = localNow(forecast.city).toLocalTime())
                cache[city.id] != null -> {
                    val stale = cache.getValue(city.id)
                    buildUiState(stale, localNow(stale.city), current.preview, current)
                        .copy(error = "Нет связи. Прогноз от ${current.updatedAt?.let(Formatters::time) ?: "—"}")
                }
                else -> {
                    val example = sample.forecast(city, localNow(city))
                    buildUiState(example, localNow(city), current.preview, current)
                        .copy(error = "Нет связи с сервером прогноза. Показан пример данных.")
                }
            }
        }
    }

    private fun prefetchCityList(cities: List<City>) {
        viewModelScope.launch {
            cities.forEach { city ->
                if (cache.containsKey(city.id)) return@forEach
                val forecast = runCatching { live.forecast(city, localNow(city)) }.getOrNull() ?: return@forEach
                cache[city.id] = forecast
                val sun = SolarCalculator.position(forecast.city, localNow(forecast.city).toLocalDate(), localNow(forecast.city).let { it.hour * 60.0 + it.minute })
                _state.update {
                    it.copy(cityWeather = it.cityWeather + (city.id to CityWeather(forecast.current.temperatureC, forecast.current.condition, sun.elevationDeg < -2)))
                }
            }
        }
    }

    private fun recompute() {
        _state.update { current ->
            val forecast = current.forecast ?: return@update current
            buildUiState(forecast, localNow(forecast.city), current.preview, current)
        }
    }

    private companion object {
        const val TICK_MS = 30_000L
        const val REFRESH_MS = 15 * 60_000L
    }
}
