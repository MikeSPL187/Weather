package app.aura.ui.scene3d

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import app.aura.SceneState
import app.aura.ui.scene.WeatherScene
import app.aura.ui.scene.rememberReduceMotion
import app.aura.ui.scene.rememberTilt
import app.aura.ui.scene.sceneDescription

/** False where the question cannot be asked, such as the JVM screenshot tests. */
private fun supportsGles3(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    manager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
}.getOrDefault(false)

/**
 * The home screen's diorama in real 3D when the device runs OpenGL ES 3, with the 2.5D Canvas
 * scene as the fallback: in previews and screenshots, on older GPUs, or if the renderer fails.
 * Drag sideways to turn the island; it drifts back on release. Long press previews other weather.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveScene(scene: SceneState, modifier: Modifier = Modifier, onLongPress: () -> Unit = {}) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    var failed by rememberSaveable { mutableStateOf(false) }
    val capable = remember { !inspection && supportsGles3(context) }
    if (!capable || failed) {
        WeatherScene(scene, modifier, onLongPress)
        return
    }
    val reduceMotion = rememberReduceMotion()
    val tilt = rememberTilt(enabled = !reduceMotion)
    var view by remember { mutableStateOf<SceneView?>(null) }
    val description = sceneDescription(scene)

    LaunchedEffect(view) {
        val v = view ?: return@LaunchedEffect
        snapshotFlow { tilt.value }.collect {
            v.tiltX = it.x
            v.tiltY = it.y
        }
    }

    Box(modifier.semantics { contentDescription = description }) {
        AndroidView(
            factory = { SceneView(it, onFailure = { failed = true }).also { created -> view = created } },
            update = { v ->
                v.target = SceneTarget(scene.landmarkCity.landmark, scene.condition, scene.sun)
                v.stillFrame = reduceMotion
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { view?.dragging = true },
                        onDragEnd = { view?.dragging = false },
                        onDragCancel = { view?.dragging = false },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            view?.let { it.dragYaw = (it.dragYaw - amount * 0.25f).coerceIn(-75f, 75f) }
                        },
                    )
                }
                .combinedClickable(onLongClickLabel = "Предпросмотр другой погоды", onLongClick = onLongPress, onClick = {}),
        )
    }
}
