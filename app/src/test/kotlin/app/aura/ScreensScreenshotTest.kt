package app.aura

import app.aura.core.data.Cities
import app.aura.core.data.SampleWeatherRepository
import app.aura.core.model.City
import app.aura.core.model.Condition
import app.aura.core.model.DataSource
import app.aura.core.model.Landmark
import app.aura.ui.AuraScaffold
import app.aura.ui.cities.CitiesScreen
import app.aura.ui.components.Tab
import app.aura.ui.theme.AuraTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test

/**
 * Renders the screens on the JVM with sample data, so the UI can be reviewed and guarded
 * against regressions without a device. Record with `:app:recordPaparazziDebug`.
 */
class ScreensScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.5,
    )

    private val repository = SampleWeatherRepository()

    /** Sample data shown as if it were live, so screenshots match what viewers see with a connection. */
    private fun state(city: City, time: LocalDateTime, condition: Condition): WeatherUiState {
        val forecast = repository.forecast(city, time, condition).copy(source = DataSource.LIVE)
        return buildUiState(forecast, time).copy(updatedAt = time.toLocalTime(), cities = Cities.defaults)
    }

    private fun screen(state: WeatherUiState, tab: Tab = Tab.NOW) = paparazzi.snapshot {
        AuraTheme { AuraScaffold(state = state, tab = tab, onTabChange = {}, onOpenCities = {}) }
    }

    private val afternoon = LocalDateTime.of(2026, 10, 22, 14, 20)

    @Test
    fun moscowClearAfternoon() = screen(state(Cities.moscow, afternoon, Condition.CLEAR))

    @Test
    fun petersburgRain() = screen(state(Cities.petersburg, LocalDateTime.of(2026, 10, 22, 13, 5), Condition.RAIN))

    @Test
    fun kazanClearNight() = screen(state(Cities.kazan, LocalDateTime.of(2026, 10, 22, 22, 30), Condition.CLEAR))

    @Test
    fun almatySnowMorning() = screen(state(Cities.almaty, LocalDateTime.of(2026, 10, 22, 9, 15), Condition.SNOW))

    @Test
    fun genericTownStormEvening() {
        val tver = City("om-tver", "Тверь", "Тверская область, Россия", 56.8587, 35.9176, 180, Landmark.GENERIC_TOWN)
        screen(state(tver, LocalDateTime.of(2026, 10, 22, 16, 40), Condition.STORM))
    }

    @Test
    fun forecastTenDays() = screen(state(Cities.moscow, afternoon, Condition.CLEAR), Tab.FORECAST)

    @Test
    fun tips() = screen(state(Cities.moscow, LocalDateTime.of(2026, 10, 22, 9, 0), Condition.CLOUDY), Tab.TIPS)

    @Test
    fun cities() {
        val base = state(Cities.moscow, afternoon, Condition.CLEAR)
        val withWeather = base.copy(
            cityWeather = mapOf(
                "msk" to CityWeather(9.4, Condition.CLEAR, false),
                "spb" to CityWeather(6.8, Condition.RAIN, false),
                "kzn" to CityWeather(5.1, Condition.CLOUDY, false),
                "ala" to CityWeather(14.6, Condition.CLEAR, true),
            ),
        )
        paparazzi.snapshot {
            AuraTheme { CitiesScreen(withWeather, onSelect = {}, onAdd = {}, onRemove = {}, onQueryChange = {}, onBack = {}) }
        }
    }

    @Test
    fun citySearch() {
        val base = state(Cities.moscow, afternoon, Condition.CLEAR).copy(
            searchQuery = "Ниж",
            searchResults = listOf(
                City("om-1", "Нижний Новгород", "Нижегородская область, Россия", 56.3287, 44.002, 180, Landmark.GENERIC_TOWN),
                City("om-2", "Нижнекамск", "Татарстан, Россия", 55.6366, 51.8245, 180, Landmark.GENERIC_TOWN),
                City("om-3", "Нижний Тагил", "Свердловская область, Россия", 57.9194, 59.965, 300, Landmark.GENERIC_TOWN),
            ),
        )
        paparazzi.snapshot {
            AuraTheme { CitiesScreen(base, onSelect = {}, onAdd = {}, onRemove = {}, onQueryChange = {}, onBack = {}) }
        }
    }
}
