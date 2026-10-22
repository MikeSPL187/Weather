package app.aura.core.data

import app.aura.core.map.RegionGrid
import app.aura.core.model.City
import app.aura.core.model.Condition
import app.aura.core.model.CurrentWeather
import app.aura.core.model.DailyForecast
import app.aura.core.model.DataSource
import app.aura.core.model.Forecast
import app.aura.core.model.HourlyForecast
import app.aura.core.model.Nowcast
import app.aura.core.model.PrecipitationKind
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** WMO weather interpretation codes, as returned by Open-Meteo, mapped to the scene's conditions. */
fun conditionForWmo(code: Int): Condition = when (code) {
    0 -> Condition.CLEAR
    1, 2 -> Condition.PARTLY_CLOUDY
    3 -> Condition.CLOUDY
    45, 48 -> Condition.FOG
    in 51..67, in 80..82 -> Condition.RAIN
    in 71..77, 85, 86 -> Condition.SNOW
    95, 96, 99 -> Condition.STORM
    else -> Condition.CLOUDY
}

private const val HPA_TO_MMHG = 0.750062
private const val NOWCAST_STEPS = 8 // 2 hours of 15-minute steps

/** The map covers about 120 km around the city: 7×7 points, 40 km apart, 12 hours ahead. */
const val GRID_SIZE = 7
const val GRID_STEP_KM = 40.0
const val GRID_HOURS = 12

object OpenMeteoUrls {
    private const val CURRENT = "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code," +
        "wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure,is_day"
    private const val HOURLY = "temperature_2m,apparent_temperature,precipitation_probability,precipitation," +
        "weather_code,wind_speed_10m,uv_index,is_day,dew_point_2m,visibility"
    private const val DAILY = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max," +
        "precipitation_sum,wind_speed_10m_max,uv_index_max,sunrise,sunset"

    fun forecast(lat: Double, lon: Double) =
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=$CURRENT&hourly=$HOURLY" +
            "&daily=$DAILY&minutely_15=precipitation&wind_speed_unit=ms&timezone=auto&forecast_days=10"

    private const val GRID_HOURLY = "precipitation,cloud_cover,temperature_2m,wind_speed_10m,wind_direction_10m"

    /** One request for every point of the map grid; the response is an array in the same order. */
    fun grid(points: List<Pair<Double, Double>>, hours: Int) =
        "https://api.open-meteo.com/v1/forecast?latitude=${points.joinToString(",") { it.first.toString() }}" +
            "&longitude=${points.joinToString(",") { it.second.toString() }}&hourly=$GRID_HOURLY" +
            "&wind_speed_unit=ms&timezone=auto&forecast_hours=$hours"

    fun airQuality(lat: Double, lon: Double) =
        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=european_aqi"

    fun search(query: String) =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}" +
            "&count=10&language=ru&format=json"
}

private fun JsonObject.obj(key: String): JsonObject? = (this[key] as? JsonObject)
private fun JsonObject.arr(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())
private fun JsonElement?.num(): Double? = if (this == null || this is JsonNull) null else jsonPrimitive.doubleOrNull
private fun JsonElement?.str(): String? = if (this == null || this is JsonNull) null else jsonPrimitive.content
private fun JsonArray.num(i: Int): Double? = getOrNull(i).num()

/** Parses Open-Meteo forecast JSON into Aura's model. Pure, so it is tested against recorded responses. */
object OpenMeteoParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, city: City, airQualityIndex: Int?): Forecast {
        val root = json.parseToJsonElement(body).jsonObject
        val offsetMinutes = (root["utc_offset_seconds"].num() ?: (city.utcOffsetMinutes * 60.0)).roundToInt() / 60
        val located = city.copy(utcOffsetMinutes = offsetMinutes)

        val current = root.obj("current") ?: error("Forecast has no current block")
        val now = LocalDateTime.parse(current["time"].str())

        val hourly = root.obj("hourly") ?: error("Forecast has no hourly block")
        val hourTimes = hourly.arr("time").map { LocalDateTime.parse(it.str()) }
        val nowHour = now.withMinute(0).withSecond(0)
        val first = hourTimes.indexOfFirst { !it.isBefore(nowHour) }.coerceAtLeast(0)
        fun h(key: String) = hourly.arr(key)
        val hours = (first until minOf(first + 24, hourTimes.size)).map { i ->
            HourlyForecast(
                time = hourTimes[i],
                temperatureC = h("temperature_2m").num(i) ?: 0.0,
                feelsLikeC = h("apparent_temperature").num(i) ?: h("temperature_2m").num(i) ?: 0.0,
                condition = conditionForWmo(h("weather_code").num(i)?.toInt() ?: 3),
                precipitationChancePercent = h("precipitation_probability").num(i)?.roundToInt() ?: 0,
                precipitationMm = h("precipitation").num(i) ?: 0.0,
                windSpeedMs = h("wind_speed_10m").num(i) ?: 0.0,
                uvIndex = h("uv_index").num(i) ?: 0.0,
                isDay = (h("is_day").num(i) ?: 1.0) > 0.5,
            )
        }

        val condition = conditionForWmo(current["weather_code"].num()?.toInt() ?: 3)
        val currentWeather = CurrentWeather(
            temperatureC = current["temperature_2m"].num() ?: 0.0,
            feelsLikeC = current["apparent_temperature"].num() ?: current["temperature_2m"].num() ?: 0.0,
            condition = condition,
            windSpeedMs = current["wind_speed_10m"].num() ?: 0.0,
            windGustMs = current["wind_gusts_10m"].num(),
            windDirectionDeg = current["wind_direction_10m"].num()?.roundToInt() ?: 0,
            humidityPercent = current["relative_humidity_2m"].num()?.roundToInt() ?: 0,
            dewPointC = h("dew_point_2m").num(first),
            pressureMmHg = ((current["surface_pressure"].num() ?: 1013.0) * HPA_TO_MMHG).roundToInt(),
            visibilityKm = h("visibility").num(first)?.div(1000),
            uvIndex = h("uv_index").num(first) ?: 0.0,
            airQualityIndex = airQualityIndex,
        )

        val daily = root.obj("daily") ?: error("Forecast has no daily block")
        fun d(key: String) = daily.arr(key)
        val days = d("time").mapIndexed { i, t ->
            DailyForecast(
                date = LocalDate.parse(t.str()),
                minC = d("temperature_2m_min").num(i) ?: 0.0,
                maxC = d("temperature_2m_max").num(i) ?: 0.0,
                condition = conditionForWmo(d("weather_code").num(i)?.toInt() ?: 3),
                precipitationChancePercent = d("precipitation_probability_max").num(i)?.roundToInt() ?: 0,
                precipitationSumMm = d("precipitation_sum").num(i) ?: 0.0,
                windSpeedMaxMs = d("wind_speed_10m_max").num(i) ?: 0.0,
                uvIndexMax = d("uv_index_max").num(i) ?: 0.0,
                sunrise = d("sunrise").getOrNull(i).str()?.let { LocalDateTime.parse(it).toLocalTime() },
                sunset = d("sunset").getOrNull(i).str()?.let { LocalDateTime.parse(it).toLocalTime() },
            )
        }

        val snowy = condition == Condition.SNOW || (hours.firstOrNull()?.temperatureC ?: 5.0) <= 0.5
        val nextTwoHoursChance = hours.take(2).maxOfOrNull { it.precipitationChancePercent } ?: 0
        val nowcast = parseNowcast(root, now, hours, nextTwoHoursChance, if (snowy) PrecipitationKind.SNOW else PrecipitationKind.RAIN)

        return Forecast(located, currentWeather, hours, days, nowcast, DataSource.LIVE)
    }

    private fun parseNowcast(
        root: JsonObject,
        now: LocalDateTime,
        hours: List<HourlyForecast>,
        chance: Int,
        kind: PrecipitationKind,
    ): Nowcast {
        val minutely = root.obj("minutely_15")
        if (minutely != null) {
            val times = minutely.arr("time").map { LocalDateTime.parse(it.str()) }
            val start = times.indexOfLast { !it.isAfter(now) }.coerceAtLeast(0)
            val values = minutely.arr("precipitation")
            val steps = (start until minOf(start + NOWCAST_STEPS, times.size)).map { (values.num(it) ?: 0.0) * 4 }
            if (steps.isNotEmpty()) return Nowcast(times[start].toLocalTime(), 15, steps, chance, kind)
        }
        // No 15-minute data for this place: fall back to the next two hours.
        val steps = hours.take(2).map { it.precipitationMm }.ifEmpty { listOf(0.0) }
        return Nowcast(now.toLocalTime().withMinute(0), 60, steps, chance, kind)
    }

    /** Parses a multi-point response for [RegionGrid.points] into hour-by-hour frames. */
    fun parseGrid(body: String, center: City, size: Int, stepKm: Double): RegionGrid {
        val locations = when (val root = json.parseToJsonElement(body)) {
            is JsonArray -> root.map { it.jsonObject }
            is JsonObject -> listOf(root)
            else -> error("Unexpected grid response")
        }
        require(locations.size == size * size) { "Expected ${size * size} points, got ${locations.size}" }
        val hourlies = locations.map { it.obj("hourly") ?: error("Grid point has no hourly block") }
        val times = hourlies.first().arr("time").map { LocalDateTime.parse(it.str()) }
        fun frames(key: String, fallback: Double) = times.indices.map { hour ->
            DoubleArray(size * size) { point -> hourlies[point].arr(key).num(hour) ?: fallback }
        }
        return RegionGrid(
            center = center,
            size = size,
            stepKm = stepKm,
            times = times,
            precipitationMmH = frames("precipitation", 0.0),
            cloudCoverPercent = frames("cloud_cover", 0.0),
            temperatureC = frames("temperature_2m", 0.0),
            windSpeedMs = frames("wind_speed_10m", 0.0),
            windDirectionDeg = frames("wind_direction_10m", 0.0),
            source = DataSource.LIVE,
        )
    }

    fun parseAirQuality(body: String): Int? =
        json.parseToJsonElement(body).jsonObject.obj("current")?.get("european_aqi").num()?.roundToInt()

    fun parseSearch(body: String, now: Instant = Instant.now()): List<City> {
        val results = json.parseToJsonElement(body).jsonObject["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val r = element.jsonObject
            val name = r["name"].str() ?: return@mapNotNull null
            val lat = r["latitude"].num() ?: return@mapNotNull null
            val lon = r["longitude"].num() ?: return@mapNotNull null
            val offset = r["timezone"].str()
                ?.let { runCatching { ZoneId.of(it).rules.getOffset(now).totalSeconds / 60 }.getOrNull() }
                ?: (lon / 15).roundToInt() * 60
            val region = listOfNotNull(r["admin1"].str(), r["country"].str()).distinct().joinToString(", ")
            City(
                id = "om-${r["id"]?.jsonPrimitive?.intOrNull ?: "$lat,$lon".hashCode()}",
                name = name,
                region = region,
                latitude = lat,
                longitude = lon,
                utcOffsetMinutes = offset,
                landmark = Cities.landmarkFor(name),
            )
        }
    }
}

/** Live forecast from Open-Meteo. Air quality is optional: the forecast still loads without it. */
class OpenMeteoWeatherRepository(private val http: HttpFetcher = UrlConnectionFetcher()) : WeatherRepository {
    override suspend fun forecast(city: City, now: LocalDateTime): Forecast = coroutineScope {
        val aqi = async { runCatching { OpenMeteoParser.parseAirQuality(http.get(OpenMeteoUrls.airQuality(city.latitude, city.longitude))) }.getOrNull() }
        val body = http.get(OpenMeteoUrls.forecast(city.latitude, city.longitude))
        OpenMeteoParser.parse(body, city, aqi.await())
    }

    /** Weather on a [size]×[size] grid [stepKm] apart around [city], for the next [hours] hours. */
    suspend fun regionGrid(city: City, size: Int = GRID_SIZE, stepKm: Double = GRID_STEP_KM, hours: Int = GRID_HOURS): RegionGrid =
        OpenMeteoParser.parseGrid(http.get(OpenMeteoUrls.grid(RegionGrid.points(city, size, stepKm), hours)), city, size, stepKm)

    suspend fun search(query: String): List<City> =
        if (query.trim().length < 2) emptyList() else OpenMeteoParser.parseSearch(http.get(OpenMeteoUrls.search(query)))
}
