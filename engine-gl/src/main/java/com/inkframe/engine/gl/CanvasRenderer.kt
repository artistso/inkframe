package com.inkframe.engine.gl

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.inkframe.core.common.Vec2
import com.inkframe.core.common.ViewportTransform
import com.inkframe.core.model.Brush
import com.inkframe.core.model.RgbaColor
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CanvasRenderer(
    private val context: Context,
    val canvasWidth: Int,
    val canvasHeight: Int,
    private val sceneProvider: () -> List<PaintEngine.LayerDrawSpec>,
    private val sculptProvider: () -> List<com.inkframe.core.model.StrokeData>,
    private val perspectiveProvider: () -> PerspectiveConfig,
    private val lassoProvider: () -> List<Vec2>,
    private val selectionProvider: () -> List<Pair<Int, Int>>,
    private val cursorProvider: () -> Vec2?,
    private val onEngineReady: (PaintEngine) -> Unit,
    private val backupStore: SurfaceBackupStore
) : GLSurfaceView.Renderer {

    private var engine: PaintEngine? = null
    private val events = ConcurrentLinkedQueue<EngineEvent>()
    var showChecker = true
    var viewport: ViewportTransform = ViewportTransform.IDENTITY

    data class PerspectiveConfig(val enabled: Boolean = false, val fisheye: Float = 0f)

    sealed interface EngineEvent {
        data class Begin(val surfaceId: Long, val brush: Brush, val color: RgbaColor, val sample: InputSample, val symmetryCount: Int) : EngineEvent
        data class Extend(val sample: InputSample) : EngineEvent
        data object End : EngineEvent
        data object Undo : EngineEvent
        data object Redo : EngineEvent
        data class Run(val block: (PaintEngine) -> Unit) : EngineEvent
    }

    fun post(event: EngineEvent) { events.add(event) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val e = PaintEngine(context, canvasWidth, canvasHeight)
        engine = e
        onEngineReady(e)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val e = engine ?: return
        while (events.isNotEmpty()) {
            val ev = events.poll() ?: break
            when (ev) {
                is EngineEvent.Begin -> e.beginStroke(ev.surfaceId, ev.brush, ev.color, ev.sample, ev.symmetryCount)
                is EngineEvent.Extend -> e.extendStroke(ev.sample)
                is EngineEvent.End -> e.endStroke()
                is EngineEvent.Undo -> e.undo()
                is EngineEvent.Redo -> e.redo()
                is EngineEvent.Run -> ev.block(e)
            }
        }
        val p = perspectiveProvider()
        e.composeAndPresent(sceneProvider(), canvasWidth, canvasHeight, showChecker, viewport.inverseCoeffs(), sculptProvider(), p.enabled, p.fisheye, lassoProvider(), selectionProvider(), cursorProvider())
    }
}
