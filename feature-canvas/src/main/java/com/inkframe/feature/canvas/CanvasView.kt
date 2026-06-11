package com.inkframe.feature.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.inkframe.core.common.Vec2
import com.inkframe.core.common.ViewportTransform
import com.inkframe.core.model.*
import com.inkframe.engine.gl.*
import java.io.*

@SuppressLint("ViewConstructor")
class CanvasView(
    context: Context,
    canvasWidth: Int,
    canvasHeight: Int,
    private val sceneProvider: () -> List<PaintEngine.LayerDrawSpec>,
    private val sculptProvider: () -> List<StrokeData> = { emptyList() },
    private val perspectiveProvider: () -> CanvasRenderer.PerspectiveConfig = { CanvasRenderer.PerspectiveConfig() },
    private val lassoProvider: () -> List<Vec2> = { emptyList() },
    private val selectionProvider: () -> List<Pair<Int, Int>> = { emptyList() },
    private val cursorProvider: () -> Vec2? = { null },
    private val strokeConfig: () -> StrokeConfig,
    private val onEngineReady: (PaintEngine) -> Unit,
) : GLSurfaceView(context) {

    data class StrokeConfig(val targetSurfaceId: Long, val brush: Brush, val color: RgbaColor, val shiftPressed: Boolean = false, val altPressed: Boolean = false, val ctrlPressed: Boolean = false)

    interface SculptListener {
        fun onNodeBegin(pos: Vec2): Boolean
        fun onNodeMove(pos: Vec2)
        fun onNodeEnd()
        fun onLassoBegin(pos: Vec2)
        fun onLassoMove(pos: Vec2)
        fun onLassoEnd()
        fun onCursorMove(pos: Vec2)
    }

    var sculptListener: SculptListener? = null
    var sculptActive: Boolean = false
    var ctrlActive: Boolean = false
    var symmetryEnabled: Boolean = false
    var symmetryCount: Int = 0

    var onViewportChanged: ((Float) -> Unit)? = null

    private val renderer = CanvasRenderer(context, canvasWidth, canvasHeight, sceneProvider, sculptProvider, perspectiveProvider, lassoProvider, selectionProvider, cursorProvider, onEngineReady, SurfaceBackupStore())

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    private var mode = Mode.IDLE
    private enum class Mode { IDLE, DRAW, NAVIGATE, SCULPT, LASSO }
    private val currentStrokePoints = ArrayList<StrokePoint>()
    private var strokeStartTime = 0L
    private var strokeOrigin: Vec2? = null

    var onStrokeFinished: ((StrokeData) -> Unit)? = null

    private var navIdA = -1
    private var navIdB = -1
    private var prevAx = 0f; private var prevAy = 0f
    private var prevBx = 0f; private var prevBy = 0f

    private fun toCanvas(vx: Float, vy: Float): Vec2 = renderer.viewport.viewToCanvas(Vec2(vx, vy))

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cfg = strokeConfig()
        
        fun sample(idx: Int, hist: Int = -1): InputSample {
            val x = if (hist >= 0) event.getHistoricalX(idx, hist) else event.getX(idx)
            val y = if (hist >= 0) event.getHistoricalY(idx, hist) else event.getY(idx)
            val p = (if (hist >= 0) event.getHistoricalPressure(idx, hist) else event.getPressure(idx)).coerceIn(0f, 1f)
            return InputSample(toCanvas(x, y), if (p <= 0f) 0.5f else p, event.eventTime)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val canvasPos = toCanvas(event.getX(0), event.getY(0))
                sculptListener?.onCursorMove(canvasPos)
                if (sculptActive) {
                    if (sculptListener?.onNodeBegin(canvasPos) == true) mode = Mode.SCULPT
                    else if (ctrlActive) { mode = Mode.LASSO; sculptListener?.onLassoBegin(canvasPos) }
                } else {
                    mode = Mode.DRAW
                    strokeStartTime = event.eventTime
                    strokeOrigin = canvasPos
                    currentStrokePoints.clear()
                    val s = sample(0)
                    currentStrokePoints.add(StrokePoint(s.pos, s.pressure, 0L))
                    renderer.post(CanvasRenderer.EngineEvent.Begin(cfg.targetSurfaceId, cfg.brush, cfg.color, s, if (symmetryEnabled) symmetryCount else 0))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val canvasPos = toCanvas(event.getX(0), event.getY(0))
                sculptListener?.onCursorMove(canvasPos)
                when (mode) {
                    Mode.DRAW -> {
                        for (h in 0 until event.historySize) {
                            val s = sample(0, h)
                            if (currentStrokePoints.size < 800) currentStrokePoints.add(StrokePoint(s.pos, s.pressure, s.timeMs - strokeStartTime))
                            renderer.post(CanvasRenderer.EngineEvent.Extend(s))
                        }
                        val s = sample(0)
                        if (currentStrokePoints.size < 800) currentStrokePoints.add(StrokePoint(s.pos, s.pressure, s.timeMs - strokeStartTime))
                        renderer.post(CanvasRenderer.EngineEvent.Extend(s))
                    }
                    Mode.SCULPT -> sculptListener?.onNodeMove(canvasPos)
                    Mode.LASSO -> sculptListener?.onLassoMove(canvasPos)
                    Mode.NAVIGATE -> updateNavigation(event)
                    else -> {}
                }
                requestRender()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode == Mode.DRAW) {
                    renderer.post(CanvasRenderer.EngineEvent.End)
                    onStrokeFinished?.invoke(StrokeData(cfg.brush.id, cfg.color, ArrayList(currentStrokePoints)))
                } else if (mode == Mode.SCULPT) sculptListener?.onNodeEnd()
                else if (mode == Mode.LASSO) sculptListener?.onLassoEnd()
                mode = Mode.IDLE
                requestRender()
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) beginNavigation(event)
            else -> {}
        }
        return true
    }

    private fun beginNavigation(event: MotionEvent) {
        mode = Mode.NAVIGATE
        navIdA = event.getPointerId(0); navIdB = event.getPointerId(1)
        prevAx = event.getX(0); prevAy = event.getY(0)
        prevBx = event.getX(1); prevBy = event.getY(1)
    }

    private fun updateNavigation(event: MotionEvent) {
        val ia = event.findPointerIndex(navIdA); val ib = event.findPointerIndex(navIdB)
        if (ia < 0 || ib < 0) return
        val curAx = event.getX(ia); val curAy = event.getY(ia)
        val curBx = event.getX(ib); val curBy = event.getY(ib)
        renderer.viewport = renderer.viewport.applyGesture(Vec2(prevAx, prevAy), Vec2(prevBx, prevBy), Vec2(curAx, curAy), Vec2(curBx, curBy))
        prevAx = curAx; prevAy = curAy; prevBx = curBx; prevBy = curBy
        onViewportChanged?.invoke(renderer.viewport.scale)
    }
    
    fun undo() { renderer.post(CanvasRenderer.EngineEvent.Undo); requestRender() }
    fun redo() { renderer.post(CanvasRenderer.EngineEvent.Redo); requestRender() }
    fun fitToScreen() { renderer.viewport = ViewportTransform.fit(canvasW, canvasH, width.toFloat(), height.toFloat()); requestRender() }
    fun resetZoom() { renderer.viewport = ViewportTransform.IDENTITY; requestRender() }
    fun setShowChecker(show: Boolean) { renderer.showChecker = show; requestRender() }
    fun runOnEngine(block: (PaintEngine) -> Unit) { renderer.post(CanvasRenderer.EngineEvent.Run(block)); requestRender() }
    
    private val canvasW get() = renderer.canvasWidth.toFloat()
    private val canvasH get() = renderer.canvasHeight.toFloat()
}
