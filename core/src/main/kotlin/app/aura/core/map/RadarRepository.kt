package app.aura.core.map

import app.aura.core.data.HttpFetcher
import app.aura.core.data.UrlConnectionFetcher

/** The last two hours of radar frames from RainViewer. */
class RadarRepository(private val http: HttpFetcher = UrlConnectionFetcher()) {
    suspend fun frames(): RadarFrames = RainViewer.parse(http.get(RainViewer.INDEX))
}
