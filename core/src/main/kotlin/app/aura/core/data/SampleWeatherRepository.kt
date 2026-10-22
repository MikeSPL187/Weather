package app.aura.core.data

import app.aura.core.model.City
import app.aura.core.model.Condition
import app.aura.core.model.CurrentWeather
import app.aura.core.model.DailyForecast
import app.aura.core.model.DataSource
import app.aura.core.model.Forecast
import app.aura.core.model.HourlyForecast
import app.aura.core.model.Nowcast
import app.aura.core.model.PrecipitationKind
import app.aura.core.solar.SolarCalculator
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.random.Random

/**
 * Deterministic sample data for screenshots, tests and offline demos. Not a forecast:
 * the screens mark it as an example whenever it is shown.
 */
class SampleWeatherRepository : WeatherRepository {

    private data class Climate(val baseC: Double, val swingC: Double)

    private val climates = mapOf(
        "msk" to Climate(6.0, 4.0),
        "spb" to Climate(5.0, 3.0),
        "kzn" to Climate(4.0, 4.0),
        "ala" to Climate(10.0, 7.0),
    )

    private val dayPattern = listOf(
        Condition.CLEAR, Condition.CLOUDY, Condition.RAIN, Condition.RAIN, Condition.CLOUDY,
        Condition.CLEAR, Condition.CLEAR, Condition.CLOUDY, Condition.SNOW, Condition.CLOUDY,
    )

    override suspend fun forecast(city: City, now: LocalDateTime): Forecast = forecast(city, now, todayCondition = null)

    /** Same sample forecast, but with today's weather forced to [todayCondition] for the demo mode. */
    fun forecast(city: City, now: LocalDateTime, todayCondition: Condition?): Forecast {
        val climate = climates[city.id] ?: Climate(5.0, 4.0)
        val random = Random(city.id.hashCode() * 31 + now.toLocalDate().toEpochDay().toInt())
        val offset = (now.toLocalDate().toEpochDay() % dayPattern.size).toInt()
        val conditions = List(10) { if (it == 0 && todayCondition != null) todayCondition else dayPattern[(it + offset) % dayPattern.size] }
        val today = conditions.first()

        fun tempAt(hour: Double, dayIndex: Int = 0): Double {
            val cool = if (conditions[dayIndex] == Condition.SNOW) -7.0 else 0.0
            return climate.baseC + cool - dayIndex * 0.4 + climate.swingC * cos(2 * PI * (hour - 15) / 24)
        }
        val wet = today == Condition.RAIN || today == Condition.STORM || today == Condition.SNOW

        val nowHour = now.hour + now.minute / 60.0
        val temperature = tempAt(nowHour)
        val start = now.withMinute(0).withSecond(0).withNano(0)
        val hourly = List(24) { i ->
            val time = start.plusHours(i.toLong())
            val dayIndex = if (time.toLocalDate() == now.toLocalDate()) 0 else 1
            val elevation = SolarCalculator.position(city, time.toLocalDate(), time.hour * 60.0).elevationDeg
            val rainyHour = wet && (i < 2 || i in 6..9)
            HourlyForecast(
                time = time,
                temperatureC = tempAt(time.hour.toDouble(), dayIndex),
                feelsLikeC = tempAt(time.hour.toDouble(), dayIndex) - 2.5,
                condition = if (wet && !rainyHour) Condition.CLOUDY else conditions[dayIndex],
                precipitationChancePercent = if (rainyHour) 65 + random.nextInt(25) else random.nextInt(12),
                precipitationMm = if (rainyHour) 0.4 + random.nextDouble() * 1.2 else 0.0,
                windSpeedMs = 2.0 + random.nextInt(5),
                uvIndex = if (elevation > 20 && today == Condition.CLEAR) 3.0 else if (elevation > 0) 1.0 else 0.0,
                isDay = elevation > -0.833,
            )
        }

        val current = CurrentWeather(
            temperatureC = temperature,
            feelsLikeC = temperature - 2.5,
            condition = today,
            windSpeedMs = 4.0,
            windGustMs = 9.0,
            windDirectionDeg = 225,
            humidityPercent = if (wet) 88 else 55,
            dewPointC = temperature - if (wet) 1.5 else 6.0,
            pressureMmHg = if (wet) 744 else 752,
            visibilityKm = when (today) {
                Condition.FOG -> 0.3
                Condition.RAIN, Condition.STORM, Condition.SNOW -> 6.0
                else -> 24.0
            },
            uvIndex = hourly.first().uvIndex,
            airQualityIndex = 28,
        )

        val daily = conditions.mapIndexed { i, condition ->
            val date = now.toLocalDate().plusDays(i.toLong())
            val sun = SolarCalculator.sunTimes(city, date)
            DailyForecast(
                date = date,
                minC = tempAt(3.0, i),
                maxC = tempAt(15.0, i),
                condition = condition,
                precipitationChancePercent = when (condition) {
                    Condition.RAIN, Condition.STORM -> 60 + random.nextInt(30)
                    Condition.SNOW -> 50 + random.nextInt(30)
                    Condition.CLOUDY -> random.nextInt(20)
                    else -> 0
                },
                precipitationSumMm = when (condition) {
                    Condition.RAIN, Condition.STORM, Condition.SNOW -> 2.0 + random.nextInt(8)
                    else -> 0.0
                },
                windSpeedMaxMs = 4.0 + random.nextInt(6),
                uvIndexMax = if (condition == Condition.CLEAR) 3.0 else 1.0,
                sunrise = sun.sunrise,
                sunset = sun.sunset,
            )
        }

        val stepMinutes = 15
        val intensities = List(8) { step ->
            when (today) {
                Condition.RAIN, Condition.STORM -> max(0.0, 2.8 - step * 1.4) + if (step in 5..6) 0.9 else 0.0
                Condition.SNOW -> 0.6 + step * 0.1
                else -> 0.0
            }
        }
        val nowcast = Nowcast(
            start = LocalTime.of(now.hour, now.minute - now.minute % stepMinutes),
            stepMinutes = stepMinutes,
            intensitiesMmH = intensities,
            precipitationChancePercent = hourly.take(2).maxOf { it.precipitationChancePercent },
            kind = if (today == Condition.SNOW) PrecipitationKind.SNOW else PrecipitationKind.RAIN,
        )
        return Forecast(city, current, hourly, daily, nowcast, DataSource.SAMPLE)
    }
}
