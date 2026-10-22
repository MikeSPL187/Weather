package app.aura

import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import app.aura.core.data.Cities
import app.aura.core.data.SampleWeatherRepository
import app.aura.core.model.City
import app.aura.core.model.Condition
import app.aura.core.model.DataSource
import app.aura.widget.AuraWidget
import app.aura.widget.WidgetContent
import app.aura.widget.WidgetSnapshot
import app.aura.widget.toWidgetSnapshot
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/** Renders the home-screen widget in its three sizes through Glance's RemoteViews, as a launcher would. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.5,
    )

    private fun snapshot(city: City, time: LocalDateTime, condition: Condition): WidgetSnapshot =
        SampleWeatherRepository().forecast(city, time, condition).copy(source = DataSource.LIVE).toWidgetSnapshot(time)

    private fun board(snapshot: WidgetSnapshot?, now: LocalDateTime) {
        val context = paparazzi.context
        val density = context.resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).roundToInt()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(0x2A, 0x2F, 0x3A))
            setPadding(px(16f), px(24f), px(16f), px(24f))
        }
        listOf(AuraWidget.SMALL, AuraWidget.WIDE, DpSize(AuraWidget.WIDE.width, AuraWidget.LARGE.height)).forEach { size ->
            val views = runBlocking { GlanceRemoteViews().compose(context, size) { WidgetContent(snapshot, now) }.remoteViews }
            val host = FrameLayout(context)
            val widget: View = views.apply(context, host)
            host.addView(widget, FrameLayout.LayoutParams(px(size.width.value), px(size.height.value)))
            column.addView(host, LinearLayout.LayoutParams(px(size.width.value), px(size.height.value)).apply { bottomMargin = px(16f) })
        }
        paparazzi.snapshot(column)
    }

    @Test
    fun moscowAfternoon() {
        val time = LocalDateTime.of(2026, 10, 22, 14, 20)
        board(snapshot(Cities.moscow, time, Condition.CLEAR), time)
    }

    @Test
    fun petersburgRainEvening() {
        val time = LocalDateTime.of(2026, 10, 22, 18, 40)
        board(snapshot(Cities.petersburg, time, Condition.RAIN), time)
    }

    @Test
    fun kazanNight() {
        val time = LocalDateTime.of(2026, 10, 22, 23, 10)
        board(snapshot(Cities.kazan, time, Condition.PARTLY_CLOUDY), time)
    }

    @Test
    fun empty() = board(null, LocalDateTime.of(2026, 10, 22, 14, 20))
}
