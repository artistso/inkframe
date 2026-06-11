package com.inkframe.engine.gl

import android.content.Context
import com.inkframe.core.common.*
import com.inkframe.core.model.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * High-level GPU paint engine.
 */
class PaintEngine(
    private val context: Context,
    val canvasWidth: Int,
    val canvasHeight: Int
) {
    private val surfaces = ConcurrentHashMap<Long, GlSurface>()
    private val brushRenderer by lazy { BrushRenderer(context) }
    private val compositor by lazy { Compositor(context, canvasWidth, canvasHeight) }

    private var strokeScratch: GlSurface? = null
    private var strokePreview: GlSurface? = null

    private var activeStroke: StrokeProcessor? = null
    private var strokeCel: GlSurface? = null
    private var strokeCelId: Long = -1L
    private var strokeBrush: Brush? = null
    private var strokeColor: RgbaColor = RgbaColor.BLACK
    private var strokeOpacity: Float = 1f
    private var symmetryCount: Int = 0
    private val dirty = DirtyRegion()

    val undoStack = UndoStack(capacity = 25)
    var onHistoryChanged: (() -> Unit)? = null

    fun getOrCreateSurface(id: Long): GlSurface {
        return surfaces.getOrPut(id) { GlSurface(canvasWidth, canvasHeight) }
    }

    private fun scratch() = strokeScratch ?: GlSurface(canvasWidth, canvasHeight).also { strokeScratch = it }
    private fun preview() = strokePreview ?: GlSurface(canvasWidth, canvasHeight).also { strokePreview = it }

    fun beginStroke(surfaceId: Long, brush: Brush, color: RgbaColor, first: InputSample, symmetry: Int = 0) {
        val cel = getOrCreateSurface(surfaceId)
        activeStroke = StrokeProcessor(brush)
        strokeCel = cel
        strokeCelId = surfaceId
        strokeBrush = brush
        strokeColor = color
        strokeOpacity = brush.opacity
        symmetryCount = symmetry
        dirty.reset()
        scratch().clear(0f, 0f, 0f, 0f)
        addDabs(activeStroke!!.add(first))
    }

    fun extendStroke(sample: InputSample) {
        val proc = activeStroke ?: return
        addDabs(proc.add(sample))
    }

    fun endStroke() {
        val cel = strokeCel ?: return
        val proc = activeStroke ?: return
        val celId = strokeCelId
        val brush = strokeBrush
        
        if (cel != null && brush != null) {
            addDabs(proc.finish())
            val topRect = dirty.toIntRect(canvasWidth, canvasHeight, padding = 2)
            if (topRect != null) {
                val glRect = topToGlRect(topRect)
                val before = cel.readPixels(glRect.x, glRect.y, glRect.w, glRect.h)
                brushRenderer.compositeScratchToCel(cel, scratch(), strokeOpacity, brush.kind == BrushKind.ERASER)
                val after = cel.readPixels(glRect.x, glRect.y, glRect.w, glRect.h)
                val snapshot = StrokeSnapshot(celId, glRect, before, after)
                undoStack.pushAlreadyApplied(StrokeCommand(snapshot, restore = { id, rect, pixels ->
                    getOrCreateSurface(id).writePixels(rect.x, rect.y, rect.w, rect.h, pixels)
                }))
                onHistoryChanged?.invoke()
            }
        }
        activeStroke = null
        strokeCel = null
        strokeCelId = -1L
    }

    private fun addDabs(dabs: List<Dab>) {
        if (dabs.isEmpty()) return
        val brush = strokeBrush ?: return
        val finalDabs = if (symmetryCount <= 1) dabs else {
            val center = Vec2(canvasWidth * 0.5f, canvasHeight * 0.5f)
            val result = ArrayList<Dab>(dabs.size * symmetryCount)
            for (i in 0 until symmetryCount) {
                val angle = (2.0 * Math.PI * i / symmetryCount).toFloat()
                val cos = Math.cos(angle.toDouble()).toFloat()
                val sin = Math.sin(angle.toDouble()).toFloat()
                for (d in dabs) {
                    val relative = d.center - center
                    result.add(d.copy(center = center + Vec2(relative.x * cos - relative.y * sin, relative.x * sin + relative.y * cos)))
                }
            }
            result
        }
        for (d in finalDabs) dirty.addCircle(d.center.x, d.center.y, d.size)
        brushRenderer.stampToScratch(scratch(), brush, strokeColor, finalDabs, brush.buildUp, brush.glowTrail, false, (System.currentTimeMillis() % 100000).toFloat() / 1000f)
    }

    private fun topToGlRect(r: IntRect) = IntRect(r.x, canvasHeight - r.y - r.h, r.w, r.h)

    fun undo() = undoStack.undo().also { if (it) onHistoryChanged?.invoke() }
    fun redo() = undoStack.redo().also { if (it) onHistoryChanged?.invoke() }
    val canUndo get() = undoStack.canUndo
    val canRedo get() = undoStack.canRedo

    fun cloneSurface(srcId: Long, dstId: Long) {
        val src = surfaces[srcId] ?: return
        val dst = getOrCreateSurface(dstId)
        brushRenderer.blit(dst, src)
    }

    fun composeAndPresent(specs: List<LayerDrawSpec>, screenW: Int, screenH: Int, showChecker: Boolean, invCoeffs: FloatArray, sculptData: List<StrokeData> = emptyList(), perspective: Boolean = false, fisheye: Float = 0f, lassoPoints: List<Vec2> = emptyList(), selectedNodes: List<Pair<Int, Int>> = emptyList(), cursorPos: Vec2? = null) {
        var previewSurface: GlSurface? = null
        val cel = strokeCel
        val brush = strokeBrush
        if (cel != null && brush != null) {
            val pv = preview()
            brushRenderer.blit(pv, cel)
            brushRenderer.compositeScratchToCel(pv, scratch(), strokeOpacity, brush.kind == BrushKind.ERASER)
            previewSurface = pv
        }

        val draws = specs.map { spec ->
            val surf = if (spec.surfaceId == strokeCelId && previewSurface != null) previewSurface else getOrCreateSurface(spec.surfaceId)
            Compositor.LayerDraw(surf, spec.opacity, spec.blendOrdinal, spec.tintR, spec.tintG, spec.tintB, spec.tintStrength)
        }
        val flat = compositor.flatten(draws)
        if (sculptData.isNotEmpty()) {
            flat.bind()
            val time = (System.currentTimeMillis() % 100000).toFloat() / 1000f
            for (si in sculptData.indices) {
                for (pi in sculptData[si].points.indices) {
                    val p = sculptData[si].points[pi]
                    val isSelected = selectedNodes.contains(Pair(si, pi))
                    brushRenderer.stampToScratch(flat, DefaultBrushes.ink, if (isSelected) RgbaColor.CYAN else RgbaColor.WHITE, listOf(Dab(p.pos, if (isSelected) 12f else 8f, 0f, 1f)), false, false, true, time)
                }
            }
        }
        compositor.present(flat, screenW, screenH, showChecker, invCoeffs, perspective, fisheye, cursorPos)
    }

    data class LayerDrawSpec(val surfaceId: Long, val opacity: Float, val blendOrdinal: Int, val tintR: Float = 0f, val tintG: Float = 0f, val tintB: Float = 0f, val tintStrength: Float = 0f)

    fun release() {
        surfaces.values.forEach { it.release() }
        strokeScratch?.release()
        strokePreview?.release()
        brushRenderer.release()
        compositor.release()
    }
}
