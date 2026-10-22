package app.aura.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class City(
    val id: String,
    val name: String,
    /** Region and country for search results and the city list, for example "Татарстан, Россия". */
    val region: String,
    val latitude: Double,
    val longitude: Double,
    /** Current offset from UTC in minutes; Russia and the CIS keep fixed offsets with no daylight saving. */
    val utcOffsetMinutes: Int,
    val landmark: Landmark,
)

/** Which island diorama the scene draws for a city. */
enum class Landmark { MOSCOW_HIGHRISE, PETERSBURG_SPIRE, KAZAN_MOSQUE, ALMATY_MOUNTAINS, GENERIC_TOWN }

enum class Condition { CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, STORM, SNOW, FOG }

data class CurrentWeather(
    val temperatureC: Double,
    val feelsLikeC: Double,
    val condition: Condition,
    val windSpeedMs: Double,
    val windGustMs: Double?,
    val windDirectionDeg: Int,
    val humidityPercent: Int,
    val dewPointC: Double?,
    val pressureMmHg: Int,
    val visibilityKm: Double?,
    val uvIndex: Double,
    /** European AQI; null when the air quality service did not answer. */
    val airQualityIndex: Int?,
)

data class HourlyForecast(
    val time: LocalDateTime,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val condition: Condition,
    val precipitationChancePercent: Int,
    val precipitationMm: Double,
    val windSpeedMs: Double,
    val uvIndex: Double,
    val isDay: Boolean,
)

data class DailyForecast(
    val date: LocalDate,
    val minC: Double,
    val maxC: Double,
    val condition: Condition,
    val precipitationChancePercent: Int,
    val precipitationSumMm: Double,
    val windSpeedMaxMs: Double,
    val uvIndexMax: Double,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
)

enum class PrecipitationKind { RAIN, SNOW }

/**
 * Precipitation for the next two hours in fixed steps, starting at [start].
 * Intensities are in millimetres per hour.
 */
data class Nowcast(
    val start: LocalTime,
    val stepMinutes: Int,
    val intensitiesMmH: List<Double>,
    /** Highest hourly probability of precipitation within the window. */
    val precipitationChancePercent: Int,
    val kind: PrecipitationKind = PrecipitationKind.RAIN,
)

enum class DataSource { LIVE, SAMPLE }

data class Forecast(
    val city: City,
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val nowcast: Nowcast,
    val source: DataSource,
)
