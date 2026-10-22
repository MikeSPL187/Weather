package app.aura.ui.map

import android.graphics.Bitmap
import android.view.Gravity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aura.core.map.MapLayer
import app.aura.core.map.MapOverlay
import app.aura.core.map.MapTimeline
import app.aura.core.map.RadarFrames
import app.aura.core.map.RainViewer
import app.aura.core.map.RegionGrid
import app.aura.core.model.City
import app.aura.ui.scene.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/** Where the camera should go: the region overview, or down into the city's 3D streets. */
enum class MapView3d { REGION, CITY }

private const val LABELS_ABOVE = "road-label"
private const val OVERLAY_PX = 160

private fun styleJson(): String =
    requireNotNull(object {}.javaClass.getResourceAsStream("/aura/map/style.json")) { "Missing map style" }.bufferedReader().use { it.readText() }

private fun camera(city: City, view: MapView3d): CameraPosition = when (view) {
    MapView3d.REGION -> CameraPosition.Builder().target(LatLng(city.latitude, city.longitude)).zoom(7.05).tilt(48.0).bearing(-12.0).build()
    MapView3d.CITY -> CameraPosition.Builder().target(LatLng(city.latitude, city.longitude)).zoom(14.6).tilt(62.0).bearing(-28.0).build()
}

private fun overlayBitmap(grid: RegionGrid, layer: MapLayer, hour: Float): Bitmap {
    val pixels = MapOverlay.render(grid, layer, hour, OVERLAY_PX)
    return Bitmap.createBitmap(pixels, OVERLAY_PX, OVERLAY_PX, Bitmap.Config.ARGB_8888)
}

private fun quad(grid: RegionGrid): LatLngQuad {
    val (north, south, west, east) = grid.bounds().toList()
    return LatLngQuad(LatLng(north, west), LatLng(north, east), LatLng(south, east), LatLng(south, west))
}

/**
 * The live map: a dark vector basemap with water, forests, roads, place names and 3D
 * buildings, and the weather over it — radar frames for the last two hours, the forecast
 * field after them, and wind drawn as moving streaks. Pans, zooms, tilts and turns freely.
 *
 * In previews and screenshot tests, where the native map cannot run, the forecast field is
 * shown on its own.
 */
@Composable
fun WeatherMap(
    city: City,
    grid: RegionGrid?,
    radar: RadarFrames?,
    timeline: MapTimeline?,
    layer: MapLayer,
    position: Float,
    view: MapView3d,
    hereLabel: String,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current || runCatching { Class.forName("app.cash.paparazzi.Paparazzi") }.isSuccess) {
        OverlayOnly(grid, layer, timeline?.moment(position)?.forecastHour ?: 0f, modifier)
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var cameraTick by remember { mutableFloatStateOf(0f) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = true
                attributionGravity = Gravity.BOTTOM or Gravity.START
                isCompassEnabled = true
                compassGravity = Gravity.TOP or Gravity.END
                setCompassMargins(0, (150 * context.resources.displayMetrics.density).toInt(), (16 * context.resources.displayMetrics.density).toInt(), 0)
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
            }
            m.cameraPosition = camera(city, MapView3d.REGION)
            m.addOnCameraMoveListener { cameraTick += 1f }
            m.addOnCameraIdleListener { cameraTick += 1f }
            m.setStyle(Style.Builder().fromJson(styleJson())) { loaded ->
                style = loaded
                map = m
            }
        }
    }

    // City and camera mode.
    LaunchedEffect(map, city.id, view) {
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(camera(city, view)), 1400)
    }

    // Forecast field: one image pinned over the grid, repainted as the slider moves.
    val moment = timeline?.moment(position)
    LaunchedEffect(style, grid, layer, moment?.forecastHour) {
        val s = style ?: return@LaunchedEffect
        val g = grid ?: return@LaunchedEffect
        val bitmap = withContext(Dispatchers.Default) { overlayBitmap(g, layer, moment?.forecastHour ?: 0f) }
        val source = s.getSourceAs<ImageSource>("forecast")
        if (source == null) {
            s.addSource(ImageSource("forecast", quad(g), bitmap))
            s.addLayerBelow(
                RasterLayer("forecast", "forecast").withProperties(
                    PropertyFactory.rasterOpacity(0.8f), PropertyFactory.rasterResampling("linear"), PropertyFactory.rasterFadeDuration(0f),
                ),
                LABELS_ABOVE,
            )
        } else {
            source.setCoordinates(quad(g))
            source.setImage(bitmap)
        }
    }

    // Values written over the field: degrees or m/s at grid points.
    val labelHour = moment?.forecastHour?.let { kotlin.math.round(it) } ?: 0f
    LaunchedEffect(style, grid, layer, labelHour) {
        val s = style ?: return@LaunchedEffect
        val g = grid ?: return@LaunchedEffect
        val features = MapOverlay.labels(g, layer, labelHour).map { label ->
            Feature.fromGeometry(Point.fromLngLat(label.longitude, label.latitude)).apply { addStringProperty("t", label.text) }
        }
        val collection = FeatureCollection.fromFeatures(features)
        val source = s.getSourceAs<GeoJsonSource>("values")
        if (source == null) {
            s.addSource(GeoJsonSource("values", collection))
            s.addLayer(
                SymbolLayer("values", "values").withProperties(
                    PropertyFactory.textField(Expression.get("t")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                    PropertyFactory.textSize(14f),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textColor("#FFFFFF"),
                    PropertyFactory.textHaloColor("rgba(10,16,48,0.85)"),
                    PropertyFactory.textHaloWidth(1.6f),
                ),
            )
        } else {
            source.setGeoJson(collection)
        }
    }

    // Radar frames: one tile layer each, shown by opacity so scrubbing never waits for tiles.
    LaunchedEffect(style, radar) {
        val s = style ?: return@LaunchedEffect
        val frames = radar ?: return@LaunchedEffect
        frames.past.forEachIndexed { i, frame ->
            val id = "radar-$i"
            if (s.getSource(id) != null) {
                s.removeLayer(id)
                s.removeSource(id)
            }
            val tiles = TileSet("2.2.0", frames.tiles(frame)).apply { maxZoom = RainViewer.MAX_ZOOM.toFloat() }
            s.addSource(RasterSource(id, tiles, 256))
            s.addLayerBelow(
                RasterLayer(id, id).withProperties(PropertyFactory.rasterOpacity(0f), PropertyFactory.rasterFadeDuration(0f), PropertyFactory.rasterResampling("linear")),
                LABELS_ABOVE,
            )
        }
    }

    LaunchedEffect(style, radar, layer, moment) {
        val s = style ?: return@LaunchedEffect
        val m = moment ?: return@LaunchedEffect
        val showRadar = layer == MapLayer.PRECIPITATION
        radar?.past?.indices?.forEach { i ->
            val opacity = when {
                !showRadar -> 0f
                i == m.radarFrom && i == m.radarTo -> m.radarOpacity
                i == m.radarFrom -> (1 - m.radarBlend) * m.radarOpacity
                i == m.radarTo -> m.radarBlend * m.radarOpacity
                else -> 0f
            }
            s.getLayer("radar-$i")?.setProperties(PropertyFactory.rasterOpacity(opacity * 0.9f))
        }
        val forecastOpacity = if (showRadar) m.forecastOpacity else 1f
        s.getLayer("forecast")?.setProperties(PropertyFactory.rasterOpacity(forecastOpacity * 0.8f))
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        val m = map
        if (m != null) {
            if (layer == MapLayer.WIND && grid != null) WindStreaks(m, grid, moment?.forecastHour ?: 0f, cameraTick)
            HereMarker(m, city, hereLabel, cameraTick)
        }
    }
}

/** Fallback without the native map: the forecast field on the dark background. */
@Composable
private fun OverlayOnly(grid: RegionGrid?, layer: MapLayer, hour: Float, modifier: Modifier) {
    Box(modifier.background(Color(0xFF101A3C)), contentAlignment = Alignment.Center) {
        if (grid != null) {
            val bitmap = remember(grid, layer, hour) { overlayBitmap(grid, layer, hour).asImageBitmap() }
            Image(bitmap, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        }
    }
}

/** The viewer's city: a pulsing dot with its name and temperature, drawn in Compose over the map. */
@Composable
private fun HereMarker(map: MapLibreMap, city: City, label: String, cameraTick: Float) {
    val reduceMotion = rememberReduceMotion()
    var time by remember { mutableFloatStateOf(0f) }
    if (!reduceMotion) LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") cameraTick
        val p = map.projection.toScreenLocation(LatLng(city.latitude, city.longitude))
        val at = Offset(p.x, p.y)
        val pulse = (time * 0.7f) % 1f
        drawCircle(Color(0xFFFFB547).copy(alpha = 0.55f * (1 - pulse)), radius = with(density) { (6 + 22 * pulse).dp.toPx() }, center = at)
        drawCircle(Color.White, radius = with(density) { 7.dp.toPx() }, center = at)
        drawCircle(Color(0xFFFFB547), radius = with(density) { 5.dp.toPx() }, center = at)
    }
    MarkerLabel(map, city, label, cameraTick)
}

/**
 * Wind as streaks that drift with the forecast wind across the grid, fading in and out,
 * like a light version of Windy. Positions are projected onto the map every frame.
 */
@Composable
private fun WindStreaks(map: MapLibreMap, grid: RegionGrid, hour: Float, cameraTick: Float) {
    val reduceMotion = rememberReduceMotion()
    var time by remember { mutableFloatStateOf(0f) }
    if (!reduceMotion) LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }
    val count = 260
    val seeds = remember(grid) { Random(11).let { r -> List(count) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    val (north, south, west, east) = remember(grid) { grid.bounds().toList() }
    val last = (grid.size - 1).toFloat()
    val buffer = remember { DoubleArray(count * 4) }
    val screen = remember { DoubleArray(count * 4) }
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") cameraTick
        seeds.forEachIndexed { i, (u0, v0, phase) ->
            val col = u0 * last
            val row = v0 * last
            val speed = grid.sample(grid.windSpeedMs, hour, col, row).toFloat()
            val to = (grid.sample(grid.windDirectionDeg, hour, col, row) + 180) * PI / 180
            val age = (time * 0.25f + phase) % 1f
            val travel = 0.02f + speed * 0.006f
            val du = sin(to).toFloat() * travel
            val dv = -cos(to).toFloat() * travel
            val u = u0 + du * age
            val v = v0 + dv * age
            val tail = 0.25f
            // Head and tail in lat/lon for one batched projection call.
            buffer[i * 4] = north + (south - north) * v
            buffer[i * 4 + 1] = west + (east - west) * u
            buffer[i * 4 + 2] = north + (south - north) * (v - dv * tail)
            buffer[i * 4 + 3] = west + (east - west) * (u - du * tail)
        }
        map.projection.toScreenLocations(buffer, screen)
        seeds.forEachIndexed { i, s ->
            val age = (time * 0.25f + s[2]) % 1f
            val alpha = sin(age * PI.toFloat()) * 0.85f
            drawLine(
                Color.White.copy(alpha = alpha),
                Offset(screen[i * 4 + 2].toFloat(), screen[i * 4 + 3].toFloat()),
                Offset(screen[i * 4].toFloat(), screen[i * 4 + 1].toFloat()),
                strokeWidth = 1.8f * density,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun MarkerLabel(map: MapLibreMap, city: City, label: String, cameraTick: Float) {
    @Suppress("UNUSED_EXPRESSION") cameraTick
    val p = map.projection.toScreenLocation(LatLng(city.latitude, city.longitude))
    Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.Text(
            label,
            style = app.aura.ui.theme.AuraType.CardTitle,
            color = app.aura.ui.theme.AuraColors.Ink,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place((p.x - placeable.width / 2f).toInt(), (p.y - placeable.height - 16 * density).toInt())
                    }
                }
                .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
