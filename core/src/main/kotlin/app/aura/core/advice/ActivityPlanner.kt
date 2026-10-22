package app.aura.core.advice

import app.aura.core.model.Condition
import app.aura.core.model.HourlyForecast
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class Activity(val label: String, val hours: Int, val idealMinC: Double, val idealMaxC: Double) {
    RUN("Пробежка", 1, 5.0, 16.0),
    BIKE("Велосипед", 2, 12.0, 24.0),
    WALK("Прогулка", 2, 10.0, 24.0),
    PHOTO("Фото", 1, -30.0, 40.0),
}

data class HourScore(val time: LocalDateTime, val score: Int)

data class ActivityWindow(
    val activity: Activity,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** 0 to 10, the average over the window. */
    val score: Int,
    val reason: String,
    val hourly: List<HourScore>,
)

/**
 * Scores every forecast hour for an activity and finds the best stretch of [Activity.hours]
 * consecutive hours. Scores fall with rain, wind, darkness and temperatures outside the
 * activity's comfortable range; photography rewards the golden hours near sunrise and sunset.
 */
object ActivityPlanner {

    fun score(activity: Activity, hour: HourlyForecast, sunrise: LocalTime?, sunset: LocalTime?): Int {
        var s = 10.0
        s -= hour.precipitationChancePercent / 15.0
        if (hour.precipitationMm >= 0.2) s -= 4
        if (hour.condition == Condition.STORM) s -= 6
        val t = hour.feelsLikeC
        val off = when {
            t < activity.idealMinC -> activity.idealMinC - t
            t > activity.idealMaxC -> t - activity.idealMaxC
            else -> 0.0
        }
        s -= off / 2.5
        val windLimit = if (activity == Activity.BIKE) 5.0 else 8.0
        if (hour.windSpeedMs > windLimit) s -= (hour.windSpeedMs - windLimit) * 0.8
        if (!hour.isDay) s -= when (activity) {
            Activity.RUN -> 2.5
            Activity.PHOTO -> 8.0
            else -> 4.5
        }
        if (activity == Activity.PHOTO) {
            val time = hour.time.toLocalTime()
            val golden = listOfNotNull(sunrise, sunset).any { abs(minutesBetween(it, time)) <= 60 }
            s += if (golden) 2.0 else -3.0
            if (hour.condition == Condition.CLEAR || hour.condition == Condition.CLOUDY) s += 1.0
        }
        return s.coerceIn(0.0, 10.0).roundToInt()
    }

    fun bestWindow(
        activity: Activity,
        hours: List<HourlyForecast>,
        sunrise: LocalTime?,
        sunset: LocalTime?,
    ): ActivityWindow? {
        if (hours.size < activity.hours) return null
        val scores = hours.map { HourScore(it.time, score(activity, it, sunrise, sunset)) }
        var bestStart = 0
        var bestSum = -1
        for (start in 0..hours.size - activity.hours) {
            val sum = (start until start + activity.hours).sumOf { scores[it].score }
            if (sum > bestSum) {
                bestSum = sum
                bestStart = start
            }
        }
        val window = hours.subList(bestStart, bestStart + activity.hours)
        val average = bestSum / activity.hours
        return ActivityWindow(
            activity = activity,
            start = window.first().time,
            end = window.last().time.plusHours(1),
            score = average,
            reason = reason(activity, window, average),
            hourly = scores,
        )
    }

    private fun reason(activity: Activity, window: List<HourlyForecast>, score: Int): String {
        val wet = window.maxOf { it.precipitationChancePercent }
        val wind = window.maxOf { it.windSpeedMs }
        val feels = window.map { it.feelsLikeC }.average()
        return when {
            score <= 3 -> "Сегодня неудачный день: ${if (wet >= 50) "дождь" else if (wind > 8) "сильный ветер" else "некомфортная температура"}. Лучше перенести."
            activity == Activity.PHOTO -> "Мягкий свет золотого часа${if (wet < 30) ", без осадков" else ""}."
            wet >= 40 -> "Самое сухое время дня, но дождь возможен: $wet %."
            wind > 7 -> "Окно с самым слабым ветром, до ${wind.roundToInt()} м/с."
            else -> "Около ${max(feels, -40.0).roundToInt()}° по ощущениям, без осадков и сильного ветра."
        }
    }

    private fun minutesBetween(a: LocalTime, b: LocalTime): Int = (b.toSecondOfDay() - a.toSecondOfDay()) / 60
}
