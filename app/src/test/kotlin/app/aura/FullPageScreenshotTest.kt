package app.aura

import app.aura.core.data.Cities
import app.aura.core.data.SampleWeatherRepository
import app.aura.core.model.Condition
import app.aura.core.model.DataSource
import app.aura.ui.AuraScaffold
import app.aura.ui.components.Tab
import app.aura.ui.theme.AuraTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test

/** The whole scrolling Now screen on a tall canvas, to review the cards below the fold. */
class FullPageScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 5600),
        useDeviceResolution = true,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.5,
    )

    @Test
    fun moscowFullPage() {
        val time = LocalDateTime.of(2026, 10, 22, 14, 20)
        val forecast = SampleWeatherRepository().forecast(Cities.moscow, time, Condition.CLEAR).copy(source = DataSource.LIVE)
        val state = buildUiState(forecast, time).copy(updatedAt = time.toLocalTime())
        paparazzi.snapshot {
            AuraTheme { AuraScaffold(state = state, tab = Tab.NOW, onTabChange = {}, onOpenCities = {}) }
        }
    }
}
