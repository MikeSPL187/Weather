package app.aura.widget

import android.content.Context
import app.aura.core.model.City
import app.aura.core.model.Condition
import app.aura.core.model.DataSource
import app.aura.core.model.Forecast
import app.aura.core.nowcast.NowcastSummarizer
import app.aura.data.toCity
import app.aura.data.toJson
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

data class WidgetHour(val time: LocalDateTime, val temperatureC: Double, val condition: Condition, val isDay: Boolean)

/** What the home-screen widget shows, saved so it can redraw without a network call. */
data class WidgetSnapshot(
    val city: City,
    val temperatureC: Double,
    val condition: Condition,
    val minC: Double?,
    val maxC: Double?,
    val headline: String,
    val hours: List<WidgetHour>,
    /** City-local time the forecast was fetched. */
    val updatedAt: LocalDateTime,
    val sample: Boolean,
) {
    /** Hours from the current one on, so the strip stays right between refreshes. */
    fun upcoming(now: LocalDateTime): List<WidgetHour> {
        val hour = now.truncatedTo(ChronoUnit.HOURS)
        return hours.filter { !it.time.isBefore(hour) }
    }

    /** The saved reading, or the forecast for this hour once the reading is more than 90 minutes old. */
    fun currentAt(now: LocalDateTime): Pair<Double, Condition> {
        val stale = Duration.between(updatedAt, now).toMinutes() > 90
        val hour = upcoming(now).firstOrNull()
        return if (stale && hour != null) hour.temperatureC to hour.condition else temperatureC to condition
    }
}

fun Forecast.toWidgetSnapshot(now: LocalDateTime): WidgetSnapshot {
    val today = daily.firstOrNull()
    return WidgetSnapshot(
        city = city,
        temperatureC = current.temperatureC,
        condition = current.condition,
        minC = today?.minC,
        maxC = today?.maxC,
        headline = NowcastSummarizer.summarize(nowcast).headline,
        hours = hourly.filter { !it.time.isBefore(now.truncatedTo(ChronoUnit.HOURS)) }.take(12)
            .map { WidgetHour(it.time, it.temperatureC, it.condition, it.isDay) },
        updatedAt = now,
        sample = source == DataSource.SAMPLE,
    )
}

/** Keeps the last widget snapshot in SharedPreferences. */
class WidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("aura_widget", Context.MODE_PRIVATE)

    fun load(): WidgetSnapshot? = prefs.getString(KEY, null)?.let { raw -> runCatching { JSONObject(raw).toSnapshot() }.getOrNull() }

    fun save(snapshot: WidgetSnapshot) {
        prefs.edit().putString(KEY, snapshot.toJson().toString()).apply()
    }

    private fun WidgetSnapshot.toJson(): JSONObject = JSONObject()
        .put("city", city.toJson())
        .put("temp", temperatureC)
        .put("condition", condition.name)
        .put("min", minC ?: JSONObject.NULL)
        .put("max", maxC ?: JSONObject.NULL)
        .put("headline", headline)
        .put("updated", updatedAt.toString())
        .put("sample", sample)
        .put("hours", JSONArray().apply {
            hours.forEach { put(JSONObject().put("t", it.time.toString()).put("c", it.temperatureC).put("w", it.condition.name).put("d", it.isDay)) }
        })

    private fun JSONObject.toSnapshot(): WidgetSnapshot {
        val hours = getJSONArray("hours")
        return WidgetSnapshot(
            city = getJSONObject("city").toCity(),
            temperatureC = getDouble("temp"),
            condition = Condition.valueOf(getString("condition")),
            minC = if (isNull("min")) null else getDouble("min"),
            maxC = if (isNull("max")) null else getDouble("max"),
            headline = getString("headline"),
            hours = List(hours.length()) { i ->
                val h = hours.getJSONObject(i)
                WidgetHour(LocalDateTime.parse(h.getString("t")), h.getDouble("c"), Condition.valueOf(h.getString("w")), h.getBoolean("d"))
            },
            updatedAt = LocalDateTime.parse(getString("updated")),
            sample = optBoolean("sample"),
        )
    }

    private companion object {
        const val KEY = "snapshot"
    }
}
