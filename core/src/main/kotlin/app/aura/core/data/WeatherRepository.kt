package app.aura.core.data

import app.aura.core.model.City
import app.aura.core.model.Forecast
import java.time.LocalDateTime

interface WeatherRepository {
    /** Forecast for [city] as seen at [now], in the city's local time. */
    suspend fun forecast(city: City, now: LocalDateTime): Forecast
}
