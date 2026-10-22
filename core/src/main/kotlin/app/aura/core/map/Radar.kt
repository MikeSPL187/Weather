package app.aura.core.map

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One radar image of the recent past, every 10 minutes. */
data class RadarFrame(val time: Instant, val path: String)

/** Observed precipitation from RainViewer's radar composite for the last two hours. */
data class RadarFrames(val host: String, val past: List<RadarFrame>) {
    /** Map tiles for [frame]: 256 px, the "Universal Blue" palette, smoothed, with snow shown apart. */
    fun tiles(frame: RadarFrame): String = "$host${frame.path}/256/{z}/{x}/{y}/2/1_1.png"
}

object RainViewer {
    const val INDEX = "https://api.rainviewer.com/public/weather-maps.json"

    /** Radar tiles exist up to this zoom; the map stretches them beyond it. */
    const val MAX_ZOOM = 7

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): RadarFrames {
        val root = json.parseToJsonElement(body).jsonObject
        val host = root["host"]?.jsonPrimitive?.content ?: "https://tilecache.rainviewer.com"
        val past = ((root["radar"] as? JsonObject)?.get("past") as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e.jsonObject
            val time = o["time"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val path = o["path"]?.jsonPrimitive?.content ?: return@mapNotNull null
            RadarFrame(Instant.ofEpochSecond(time), path)
        }
        return RadarFrames(host, past.sortedBy { it.time })
    }
}
