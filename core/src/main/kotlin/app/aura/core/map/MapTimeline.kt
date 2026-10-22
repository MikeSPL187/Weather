package app.aura.core.map

import app.aura.core.format.Formatters
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * One slider over observed radar frames for the last two hours and the forecast after them.
 * Positions are frames: radar frames 10 minutes apart up to "now", then forecast hours.
 */
class MapTimeline(val radar: List<RadarFrame>, val forecast: List<LocalDateTime>, private val utcOffsetMinutes: Int) {

    /** Where "now" sits on the slider: the latest radar frame, or the first forecast hour. */
    val now: Int = (radar.size - 1).coerceAtLeast(0)

    val last: Int = now + (forecast.size - 1).coerceAtLeast(0)

    /** What to show at [position]: radar frames and their blend, and the forecast hour and its opacity. */
    data class Moment(val radarFrom: Int, val radarTo: Int, val radarBlend: Float, val radarOpacity: Float, val forecastHour: Float, val forecastOpacity: Float)

    fun moment(position: Float): Moment {
        val p = position.coerceIn(0f, last.toFloat())
        if (radar.isEmpty()) return Moment(-1, -1, 0f, 0f, p, 1f)
        if (p <= now) {
            val from = floor(p).toInt().coerceAtMost(now)
            val to = (from + 1).coerceAtMost(now)
            return Moment(from, to, p - from, 1f, 0f, 0f)
        }
        // The first forecast hour cross-fades from the last radar picture.
        val ahead = p - now
        val fade = ahead.coerceIn(0f, 1f)
        return Moment(now, now, 0f, 1 - fade, ahead, fade)
    }

    private fun local(frame: RadarFrame): LocalDateTime =
        LocalDateTime.ofInstant(frame.time, ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60))

    /** "Сейчас · 14:00", "−40 мин · 13:20" for radar, "+3 ч · 17:00" for the forecast. */
    fun label(position: Float): String {
        val step = position.roundToInt().coerceIn(0, last)
        return when {
            step == now -> "Сейчас · " + time(step)
            step < now -> {
                val minutes = Duration.between(local(radar[step]), local(radar[now])).toMinutes()
                "−$minutes мин · " + time(step)
            }
            else -> "+${step - now} ч · " + time(step)
        }
    }

    fun time(step: Int): String = when {
        step < radar.size && (step < now || forecast.isEmpty()) -> Formatters.time(local(radar[step]).toLocalTime())
        step == now && radar.isNotEmpty() -> Formatters.time(local(radar[now]).toLocalTime())
        else -> Formatters.time(forecast[(step - now).coerceIn(0, forecast.lastIndex)].toLocalTime())
    }

    fun isRadar(position: Float): Boolean = radar.isNotEmpty() && position.roundToInt() <= now

    /** Forecast hour that matches [position], for the per-layer summaries. */
    fun forecastStep(position: Float): Int = (position.roundToInt() - now).coerceIn(0, (forecast.size - 1).coerceAtLeast(0))
}
