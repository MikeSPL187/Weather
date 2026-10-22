package app.aura.core.data

import app.aura.core.model.City
import app.aura.core.model.Condition
import app.aura.core.model.CurrentWeather
import app.aura.core.model.DailyForecast
import app.aura.core.model.Forecast
import app.aura.core.model.HourlyForecast
import app.aura.core.model.Nowcast
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.random.Random

/**
 * Deterministic sample data for building and testing the UI before a real
 * forecast source (Meteum, Open-Meteo) is connected. Not a forecast.
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

    private val summaries = mapOf(
        Condition.CLEAR to "Ясно и сухо, лучший день для прогулки.",
        Condition.CLOUDY to "Облачно, без существенных осадков.",
        Condition.RAIN to "Дождь днём, местами сильный. Возьмите зонт.",
        Condition.STORM to "Гроза во второй половине дня, порывы ветра.",
        Condition.SNOW to "Мокрый снег, гололедица на дорогах.",
        Condition.FOG to "Туман утром, к обеду рассеется.",
    )

    override suspend fun forecast(city: City, now: LocalDateTime): Forecast {
        val climate = climates[city.id] ?: Climate(5.0, 4.0)
        val random = Random(city.id.hashCode() * 31 + now.toLocalDate().toEpochDay().toInt())
        val offset = (now.toLocalDate().toEpochDay() % dayPattern.size).toInt()
        val conditions = List(10) { dayPattern[(it + offset) % dayPattern.size] }
        val today = conditions.first()

        fun tempAt(hour: Double, dayIndex: Int = 0): Double {
            val cool = if (conditions[dayIndex] == Condition.SNOW) -7.0 else 0.0
            return climate.baseC + cool - dayIndex * 0.4 + climate.swingC * cos(2 * PI * (hour - 15) / 24)
        }

        val nowHour = now.hour + now.minute / 60.0
        val temperature = tempAt(nowHour)
        val current = CurrentWeather(
            temperatureC = temperature,
            feelsLikeC = temperature - 2,
            condition = today,
            windSpeedMs = 2.0 + random.nextInt(5),
            windDirectionDeg = 225,
            humidityPercent = 45 + random.nextInt(40),
            pressureMmHg = 748 + random.nextInt(10),
            uvIndex = if (nowHour in 9.0..16.0 && today == Condition.CLEAR) 3 else 1,
            airQualityIndex = 20 + random.nextInt(30),
        )

        val hourly = List(24) { i ->
            val hour = (now.hour + i) % 24
            HourlyForecast(
                time = LocalTime.of(hour, 0),
                temperatureC = tempAt(now.hour + i.toDouble()),
                condition = today,
                precipitationChancePercent = if (today == Condition.RAIN) 60 + random.nextInt(30) else random.nextInt(10),
            )
        }

        val daily = conditions.mapIndexed { i, condition ->
            DailyForecast(
                date = now.toLocalDate().plusDays(i.toLong()),
                minC = tempAt(5.0, i),
                maxC = tempAt(15.0, i),
                condition = condition,
                precipitationChancePercent = when (condition) {
                    Condition.RAIN, Condition.STORM -> 60 + random.nextInt(30)
                    Condition.SNOW -> 50 + random.nextInt(30)
                    Condition.CLOUDY -> random.nextInt(20)
                    else -> 0
                },
                summary = summaries.getValue(condition),
                windSpeedMs = 2.0 + random.nextInt(6),
                uvIndex = if (condition == Condition.CLEAR) 3 else 1,
                humidityPercent = 50 + random.nextInt(45),
            )
        }

        val stepMinutes = 5
        val intensities = List(24) { step ->
            when (today) {
                Condition.RAIN, Condition.STORM -> max(0.0, 2.8 - step * 0.55) + if (step in 16..21) 0.9 else 0.0
                Condition.SNOW -> 0.6 + step * 0.03
                else -> 0.0
            }
        }
        val nowcast = Nowcast(
            start = LocalTime.of(now.hour, now.minute - now.minute % stepMinutes),
            stepMinutes = stepMinutes,
            intensitiesMmH = intensities,
            confidencePercent = if (today == Condition.CLEAR) 94 else 87,
        )
        return Forecast(city, current, hourly, daily, nowcast)
    }
}
