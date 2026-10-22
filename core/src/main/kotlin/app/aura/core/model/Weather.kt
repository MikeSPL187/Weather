package app.aura.core.model

import java.time.LocalDate
import java.time.LocalTime

data class City(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Offset from UTC in hours. All supported cities have fixed offsets with no daylight saving. */
    val utcOffsetHours: Int,
    val landmark: Landmark,
)

/** Which island diorama the scene draws for a city. */
enum class Landmark { MOSCOW_HIGHRISE, PETERSBURG_SPIRE, KAZAN_MOSQUE, ALMATY_MOUNTAINS }

enum class Condition { CLEAR, CLOUDY, RAIN, STORM, SNOW, FOG }

data class CurrentWeather(
    val temperatureC: Double,
    val feelsLikeC: Double,
    val condition: Condition,
    val windSpeedMs: Double,
    val windDirectionDeg: Int,
    val humidityPercent: Int,
    val pressureMmHg: Int,
    val uvIndex: Int,
    val airQualityIndex: Int,
)

data class HourlyForecast(
    val time: LocalTime,
    val temperatureC: Double,
    val condition: Condition,
    val precipitationChancePercent: Int,
)

data class DailyForecast(
    val date: LocalDate,
    val minC: Double,
    val maxC: Double,
    val condition: Condition,
    val precipitationChancePercent: Int,
    val summary: String,
    val windSpeedMs: Double,
    val uvIndex: Int,
    val humidityPercent: Int,
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
    val confidencePercent: Int,
    val kind: PrecipitationKind = PrecipitationKind.RAIN,
)

data class Forecast(
    val city: City,
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val nowcast: Nowcast,
)
