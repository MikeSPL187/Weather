package app.aura

import app.aura.core.data.Cities
import app.aura.core.data.SampleWeatherRepository
import app.aura.core.model.City
import app.aura.core.model.Condition
import app.aura.ui.AuraScaffold
import app.aura.ui.cities.CitiesScreen
import app.aura.ui.components.Tab
import app.aura.ui.theme.AuraTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/**
 * Renders the main screens on the JVM with sample data, so the UI can be reviewed and
 * guarded against regressions without a device. Record with `:app:recordPaparazziDebug`.
 */
class ScreensScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.5,
    )

    private val repository = SampleWeatherRepository()

    private fun state(city: City, time: LocalDateTime, condition: Condition): WeatherUiState = runBlocking {
        buildUiState(repository.forecast(city, time, condition), time, demoCondition = condition)
    }

    private fun now(state: WeatherUiState, tab: Tab = Tab.NOW) = paparazzi.snapshot {
        AuraTheme {
            AuraScaffold(state = state, tab = tab, onTabChange = {}, onOpenCities = {}, onSceneTap = {})
        }
    }

    @Test
    fun moscowClearAfternoon() = now(state(Cities.moscow, LocalDateTime.of(2026, 10, 22, 14, 20), Condition.CLEAR))

    @Test
    fun petersburgRain() = now(state(Cities.petersburg, LocalDateTime.of(2026, 10, 22, 13, 5), Condition.RAIN))

    @Test
    fun kazanClearNight() = now(state(Cities.kazan, LocalDateTime.of(2026, 10, 22, 22, 30), Condition.CLEAR))

    @Test
    fun almatySnowMorning() = now(state(Cities.almaty, LocalDateTime.of(2026, 10, 22, 9, 15), Condition.SNOW))

    @Test
    fun forecastTenDays() = now(state(Cities.moscow, LocalDateTime.of(2026, 10, 22, 14, 20), Condition.CLEAR), Tab.FORECAST)

    @Test
    fun cities() = paparazzi.snapshot {
        AuraTheme { CitiesScreen(selected = Cities.moscow, onSelect = {}, onBack = {}) }
    }
}
