package com.inkframe.feature.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.inkframe.core.model.BlendMode
import com.inkframe.core.model.Brush
import com.inkframe.core.model.CanvasSpec
import com.inkframe.core.model.Cel
import com.inkframe.core.model.DefaultBrushes
import com.inkframe.core.model.Layer
import com.inkframe.core.model.LayerOps
import com.inkframe.core.model.PlaybackOps
import com.inkframe.core.model.OnionGhost
import com.inkframe.core.model.OnionSkinPlanner
import com.inkframe.core.model.OnionSkinSettings
import com.inkframe.core.model.Project
import com.inkframe.core.model.RecentColors
import com.inkframe.core.model.RgbaColor
import com.inkframe.core.model.Scene
import com.inkframe.core.model.TimelineOps
import com.inkframe.engine.gl.PaintEngine
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import com.inkframe.core.common.ShapeDetector
import com.inkframe.core.common.Vec2
import com.inkframe.core.common.VectorMath

/**
 * Observable studio state. Holds the document [Project] plus the current editing
 * context (active scene/layer/frame, brush, color, playback state). The Compose UI
 * reads from here; the canvas writes pixels via the engine and only updates model
 * structure (which cel exists) through this class.
 */
class StudioState : ViewModel() {

    var project by mutableStateOf(newProject())
        private set

    var activeLayerId by mutableStateOf(project.activeScene!!.layers.first().id)
    var currentFrame by mutableStateOf(0)
        private set
    var brush by mutableStateOf<Brush>(DefaultBrushes.ink)
    var color by mutableStateOf(RgbaColor.BLACK)
    /** Most-recently-used colours for the picker's "recent" row. */
    var recentColors by mutableStateOf(RecentColors.empty())
        private set
    /** Whether the colour picker dialog is open. */
    var showColorPicker by mutableStateOf(false)
    /** Whether the eyedropper tool is armed (next canvas tap samples a colour). */
    var eyedropperActive by mutableStateOf(false)
    /** Whether the "Sculpt" (Quantum Path) mode is active. */
    var sculptMode by mutableStateOf(false)
    /** Whether "Lasso" selection mode is active. */
    var lassoMode by mutableStateOf(false)
    var lassoPath = mutableStateListOf<Vec2>()
    var selectedNodes = mutableStateListOf<Pair<Int, Int>>() // (StrokeIdx, PointIdx)
    
    /** The current logical position of the "Precision Cursor" in canvas space. */
    var cursorPosition by mutableStateOf<Vec2?>(null)
    /** The node currently hovered by the cursor. */
    var hoveredNode by mutableStateOf<Pair<Int, Int>?>(null)

    /** The node currently being dragged in Sculpt Mode: (StrokeIndex, PointIndex) */
    var activeSculptNode by mutableStateOf<Pair<Int, Int>?>(null)
    /** Whether the bucket/fill tool is armed (next canvas tap flood-fills). */
    var fillActive by mutableStateOf(false)
    /** Whether the brush settings panel is open. */
    var showBrushSettings by mutableStateOf(false)
    /** Whether the brush library (plus button) is open. */
    var showBrushLibrary by mutableStateOf(false)
    /** Whether the timeline is expanded in its Fibonacci shell state. */
    var timelineExpanded by mutableStateOf(false)
    /** Whether the current brush's integrated settings are expanded. */
    var brushSettingsExpanded by mutableStateOf(false)
    /** Whether the VFX Telemetry HUD is active. */
    var showVfxHud by mutableStateOf(false)
    /** Id of the layer currently being renamed (shows the rename dialog), or null. */
    var renamingLayerId by mutableStateOf<String?>(null)

    /**
     * Sets the active colour and records the *previous* colour in recents, so the recent
     * row fills with colours the artist has actually committed to (not every intermediate
     * value dragged through on a slider). Call when a colour is confirmed/selected.
     */
    fun commitColor(newColor: RgbaColor) {
        if (newColor.toArgb() != color.toArgb()) {
            recentColors = recentColors.add(color)
        }
        color = newColor
    }
    /** Multi-frame onion-skin configuration (range, falloff, tints). */
    var onionSkin by mutableStateOf(OnionSkinSettings())
    /** Whether the onion-skin settings panel is open. */
    var showOnionSettings by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
        private set
    var showChecker by mutableStateOf(true)

    // --- KJG & Proko Influences ---------------------------------------------
    var perspectiveEnabled by mutableStateOf(false)
    var perspectiveFisheye by mutableStateOf(0.5f) // 0 = Linear, 1 = KJG Fish-eye
    var structureMode by mutableStateOf(false) // Proko "Lay-in" mode (lowers opacity, changes grit)

    // Mirror the engine's history availability for the toolbar buttons.
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    // Persistence status surfaced to the UI (e.g. a snackbar / title suffix).
    var statusMessage by mutableStateOf<String?>(null)
    var isBusy by mutableStateOf(false)
        private set

    /** Current viewport zoom as a percentage, shown in the toolbar. */
    var zoomPercent by mutableStateOf(100)
        private set

    // --- Modifier Keys (Desktop-style precision) ----------------------------
    var ctrlPressed by mutableStateOf(false)
    var shiftPressed by mutableStateOf(false)
    var altPressed by mutableStateOf(false)

    // --- Symmetry Mode (Fibonacci pivot based) ------------------------------
    var symmetryEnabled by mutableStateOf(false)
    var symmetryCount by mutableStateOf(4) // 2, 4, 6, 8, etc.

    fun setZoom(scale: Float) { zoomPercent = (scale * 100f).toInt().coerceAtLeast(1) }

    val scene: Scene get() = project.activeScene!!
    val activeLayer: Layer get() = scene.layerById(activeLayerId) ?: scene.layers.first()

    // --- Engine wiring -------------------------------------------------------

    private val surfaceIds = AtomicLong(1L)
    @Volatile private var engine: PaintEngine? = null

    /** Posted by the engine (GL thread) so the UI can refresh on the main thread. */
    var onUiInvalidate: (() -> Unit)? = null

    /**
     * Called from the renderer once the GL context/engine exists. Hooks the engine's
     * history callback so the undo/redo button enabled-state stays in sync. The callback
     * fires on the GL thread, so we marshal the snapshot of flags and let the host repost
     * to the main thread via [onUiInvalidate].
     */
    fun bindEngine(e: PaintEngine) {
        engine = e
        e.onHistoryChanged = {
            canUndo = e.canUndo
            canRedo = e.canRedo
            onUiInvalidate?.invoke()
        }
    }

    /**
     * Returns the surface id for the active cel at the current frame, minting a new id
     * (and recording the cel in the model) if none exists yet. The GPU surface itself is
     * created lazily on the GL thread on first draw.
     */
    fun ensureActiveCel(): Long {
        val layer = activeLayer
        val existing = layer.cels[currentFrame]
        if (existing != null) return existing.surfaceId
        val sid = surfaceIds.getAndIncrement()
        updateLayer(layer.id) { it.copy(cels = it.cels + (currentFrame to Cel(surfaceId = sid))) }
        return sid
    }

    /**
     * Builds the bottom-to-top composite for the current frame, including onion-skin
     * ghosts of the active layer's previous/next cels at reduced opacity.
     */
    /**
     * Builds the flattened bottom-to-top draw list for an arbitrary [frame], honouring
     * layer visibility, opacity, blend mode and frame-holds — but WITHOUT onion skinning.
     * Used by the export pipeline to render any timeline frame independent of the current
     * editing frame.
     */
    fun buildExportDrawList(frame: Int): List<PaintEngine.LayerDrawSpec> {
        val specs = ArrayList<PaintEngine.LayerDrawSpec>()
        for (layer in scene.layers) {
            if (!layer.visible) continue
            val cel = layer.celAt(frame) ?: continue
            specs += PaintEngine.LayerDrawSpec(cel.surfaceId, layer.opacity, layer.blendMode.ordinal)
        }
        return specs
    }

    fun buildDrawList(): List<PaintEngine.LayerDrawSpec> {
        val specs = ArrayList<PaintEngine.LayerDrawSpec>()
        for (layer in scene.layers) {
            if (!layer.visible) continue
            // Onion-skin ghosts (only for the active layer), composited below its drawing.
            if (layer.id == activeLayerId) {
                val ghosts = OnionSkinPlanner.plan(currentFrame, onionSkin) { frame ->
                    layer.cels[frame]?.surfaceId
                }
                for (g in ghosts) specs += g.toSpec(layer.opacity, layer.blendMode.ordinal)
            }
            val cel = layer.celAt(currentFrame) ?: continue
            specs += PaintEngine.LayerDrawSpec(cel.surfaceId, layer.opacity, layer.blendMode.ordinal)
        }
        return specs
    }

    private fun OnionGhost.toSpec(layerOpacity: Float, blendOrdinal: Int) =
        PaintEngine.LayerDrawSpec(
            surfaceId = surfaceId,
            opacity = (opacity * layerOpacity).coerceIn(0f, 1f),
            blendOrdinal = blendOrdinal,
            tintR = tint.r, tintG = tint.g, tintB = tint.b,
            tintStrength = tintStrength,
        )

    fun setBusy(busy: Boolean) { isBusy = busy }

    /**
     * Replaces the in-memory document after a successful load. Resets the editing context
     * to the loaded project's first scene/layer/frame and advances the surface-id counter
     * past every id used by the document so newly drawn cels never collide.
     */
    fun replaceProject(loaded: Project) {
        project = loaded
        val firstScene = loaded.activeScene ?: loaded.scenes.firstOrNull()
        activeLayerId = firstScene?.layers?.firstOrNull()?.id ?: activeLayerId
        currentFrame = 0
        val maxId = loaded.scenes
            .flatMap { it.layers }
            .flatMap { it.cels.values }
            .maxOfOrNull { it.surfaceId } ?: 0L
        surfaceIds.set(maxId + 1)
        isPlaying = false
    }

    fun setFrame(frame: Int) {
        currentFrame = frame.coerceIn(0, scene.frameCount - 1)
    }

    fun togglePlay() { isPlaying = !isPlaying }
    fun stop() { isPlaying = false }

    /** Milliseconds per frame at the project frame rate (drives the playback loop). */
    val frameDurationMs: Long get() = PlaybackOps.frameDurationMs(project.canvas.fps)

    fun advancePlayback() {
        val (next, stillPlaying) = PlaybackOps.nextFrame(currentFrame, scene.playbackRange, scene.loop)
        currentFrame = next
        if (!stillPlaying) isPlaying = false
    }

    // --- Playback range (in/out points), FPS, loop ---------------------------

    /** Sets the loop in-point to the current frame (pushes the out-point if needed). */
    fun setInPointToCurrent() = updateScene {
        it.copy(playbackRange = PlaybackOps.setInPoint(it.playbackRange, currentFrame, it.frameCount))
    }

    /** Sets the loop out-point to the current frame (pulls the in-point if needed). */
    fun setOutPointToCurrent() = updateScene {
        it.copy(playbackRange = PlaybackOps.setOutPoint(it.playbackRange, currentFrame, it.frameCount))
    }

    /** Resets the playback range to the whole timeline. */
    fun clearPlaybackRange() = updateScene {
        it.copy(playbackRange = PlaybackOps.fullRange(it.frameCount))
    }

    fun toggleLoop() = updateScene { it.copy(loop = !it.loop) }

    /** Changes the project frame rate (clamped to the supported range). */
    fun setFps(fps: Int) {
        project = project.copy(
            canvas = project.canvas.copy(fps = PlaybackOps.clampFps(fps)),
            modifiedAtEpochMs = System.currentTimeMillis(),
        )
    }

    fun addLayer(name: String = "Layer ${scene.layers.size + 1}") {
        val layer = Layer(name = name)
        updateScene { it.copy(layers = it.layers + layer) }
        activeLayerId = layer.id
    }

    // --- Layer management ----------------------------------------------------

    /** Moves a layer one step toward the top of the stack (composited later/over). */
    fun moveLayerUp(id: String) = updateScene { LayerOps.moveUp(it, id) }

    /** Moves a layer one step toward the bottom of the stack. */
    fun moveLayerDown(id: String) = updateScene { LayerOps.moveDown(it, id) }

    fun renameLayer(id: String, name: String) = updateScene { LayerOps.rename(it, id, name) }

    fun toggleLayerVisible(id: String) = updateScene { LayerOps.toggleVisible(it, id) }

    fun toggleLayerLocked(id: String) = updateScene { LayerOps.toggleLocked(it, id) }

    fun setLayerOpacity(id: String, opacity: Float) = updateScene { LayerOps.setOpacity(it, id, opacity) }

    fun setLayerBlendMode(id: String, mode: BlendMode) = updateScene { LayerOps.setBlendMode(it, id, mode) }

    fun swapLayers(fromId: String, toId: String) {
        updateScene { sc ->
            val fromIdx = sc.layers.indexOfFirst { it.id == fromId }
            val toIdx = sc.layers.indexOfFirst { it.id == toId }
            if (fromIdx == -1 || toIdx == -1) return@updateScene sc
            val list = sc.layers.toMutableList()
            val moved = list.removeAt(fromIdx)
            list.add(toIdx, moved)
            sc.copy(layers = list)
        }
    }

    fun duplicateLayer(id: String) {
        val nextId = UUID.randomUUID().toString()
        val originalLayer = scene.layerById(id) ?: return
        
        // Model duplication
        updateScene { sc ->
            val idx = sc.layers.indexOfFirst { it.id == id }
            val newLayer = originalLayer.copy(
                id = nextId,
                name = "${originalLayer.name} (Copy)",
                cels = originalLayer.cels.mapValues { (_, cel) ->
                    val newSid = surfaceIds.getAndIncrement()
                    // Engine must clone the pixels
                    postEngineWork?.invoke { engine -> engine.cloneSurface(cel.surfaceId, newSid) }
                    cel.copy(id = UUID.randomUUID().toString(), surfaceId = newSid)
                }
            )
            val list = sc.layers.toMutableList()
            list.add(idx + 1, newLayer)
            sc.copy(layers = list)
        }
        activeLayerId = nextId
    }

    /**
     * Deletes a layer, keeping at least one in the scene and re-selecting a sensible
     * active layer if the deleted one was active.
     */
    fun deleteLayer(id: String) {
        val nextActive = LayerOps.activeAfterDelete(scene, id, activeLayerId)
        updateScene { LayerOps.delete(it, id) }
        activeLayerId = nextActive
    }

    /** Applies a validated edit to the current brush (from the settings panel). */
    fun updateBrush(transform: (Brush) -> Brush) {
        brush = transform(brush)
    }

    // --- Timeline editing ----------------------------------------------------

    /**
     * Posts GPU work to the engine on the GL thread. Used by duplicate/paste to clone a
     * source surface into a fresh one. [requestRender] is supplied by the host so the
     * canvas redraws once the clone lands.
     */
    var postEngineWork: ((block: (PaintEngine) -> Unit) -> Unit)? = null

    /** A copied/cut cel kept for paste. The pixels stay on its [Cel.surfaceId]. */
    var clipboardCel by mutableStateOf<Cel?>(null)
        private set

    val canPaste: Boolean get() = clipboardCel != null
    val hasCelAtCurrentFrame: Boolean get() = activeLayer.cels.containsKey(currentFrame)

    /** True if the active layer has an explicit (drawn) cel at [frame] — drag source test. */
    fun hasCelAt(frame: Int): Boolean = activeLayer.cels.containsKey(frame)

    /**
     * Moves the active layer's explicit cel from [from] to [to] (drag-to-move on the
     * timeline). The cel keeps its surfaceId — only its timeline position changes — so no
     * GPU work is needed. Selects the destination frame afterwards. No-op if there's no
     * cel at [from] or the indices are equal.
     */
    fun moveCel(from: Int, to: Int) {
        if (from == to) return
        if (!activeLayer.cels.containsKey(from)) return
        val dest = to.coerceIn(0, scene.frameCount - 1)
        updateLayer(activeLayerId) { TimelineOps.moveCel(it, from, dest) }
        currentFrame = dest
    }

    /** Clears the explicit cel at the current frame on the active layer. */
    fun clearCelAtCurrentFrame() {
        updateLayer(activeLayerId) { TimelineOps.clearCel(it, currentFrame) }
    }

    /** Copies the current frame's explicit cel to the clipboard. */
    fun copyCel() {
        clipboardCel = TimelineOps.explicitCel(activeLayer, currentFrame)
    }

    /** Cut = copy then clear. */
    fun cutCel() {
        val cel = TimelineOps.explicitCel(activeLayer, currentFrame) ?: return
        clipboardCel = cel
        updateLayer(activeLayerId) { TimelineOps.clearCel(it, currentFrame) }
    }

    /**
     * Duplicates the current frame's cel onto the next frame as an independent drawing,
     * cloning its pixels into a fresh surface, then advances to it.
     */
    fun duplicateCelToNextFrame() {
        val src = TimelineOps.explicitCel(activeLayer, currentFrame) ?: return
        val to = currentFrame + 1
        val newId = surfaceIds.getAndIncrement()
        // Ensure room on the timeline if duplicating past the end.
        if (to >= scene.frameCount) updateScene { TimelineOps.insertFrames(it, scene.frameCount, to - scene.frameCount + 1) }
        updateLayer(activeLayerId) { TimelineOps.duplicateCel(it, currentFrame, to, newId) }
        postEngineWork?.invoke { engine -> engine.cloneSurface(src.surfaceId, newId) }
        currentFrame = to
    }

    /** Pastes the clipboard cel onto the current frame as an independent drawing. */
    fun pasteCel() {
        val clip = clipboardCel ?: return
        val newId = surfaceIds.getAndIncrement()
        updateLayer(activeLayerId) { TimelineOps.pasteCel(it, currentFrame, clip, newId) }
        postEngineWork?.invoke { engine -> engine.cloneSurface(clip.surfaceId, newId) }
    }

    /** Inserts a blank frame at the current position (shifts later cels right). */
    fun insertFrame() {
        updateScene { TimelineOps.insertFrames(it, currentFrame, 1) }
    }

    /** Removes the current frame across all layers (shifts later cels left). */
    fun removeFrame() {
        updateScene { TimelineOps.removeFrames(it, currentFrame, 1) }
        currentFrame = currentFrame.coerceIn(0, scene.frameCount - 1)
    }

    /** Holds the current drawing [holdFrames] longer by inserting frames after it. */
    fun extendExposure(holdFrames: Int = 1) {
        updateScene { TimelineOps.extendExposure(it, currentFrame, holdFrames) }
    }

    fun updateLayer(id: String, transform: (Layer) -> Layer) {
        updateScene { sc -> sc.copy(layers = sc.layers.map { if (it.id == id) transform(it) else it }) }
    }

    /** Records a finished vector stroke into the model for the active cel. */
    fun recordStroke(data: com.inkframe.core.model.StrokeData) {
        val layer = activeLayer
        val cel = layer.cels[currentFrame] ?: return
        val existingStrokes = cel.vectorData.strokes
        val existingPaths = existingStrokes.map { it.points.map { p -> p.pos } }

        var processedPoints = data.points

        // 1. Vector Magnet & Merging (CSP style)
        var merged = false
        val finalData = if (brush.vectorMagnet > 0f) {
            val threshold = brush.vectorMagnet * 50f
            var resultData = data
            
            // Try to merge with an existing stroke of the same brush/color
            val mergeIdx = existingStrokes.indexOfFirst { 
                it.brushId == data.brushId && it.color == data.color 
            }
            
            if (mergeIdx != -1) {
                val pathA = existingStrokes[mergeIdx].points.map { it.pos }
                val pathB = processedPoints.map { it.pos }
                val mergedPath = VectorMath.tryMerge(pathA, pathB, threshold)
                
                if (mergedPath != null) {
                    val mergedPoints = mergedPath.map { pos ->
                        // Re-use pressure from original points where possible
                        (existingStrokes[mergeIdx].points + processedPoints)
                            .minByOrNull { it.pos.distanceTo(pos) }?.copy(pos = pos)
                            ?: com.inkframe.core.model.StrokePoint(pos, 0.5f, 0L)
                    }
                    val newStrokes = existingStrokes.toMutableList()
                    newStrokes[mergeIdx] = existingStrokes[mergeIdx].copy(points = mergedPoints)
                    merged = true
                    statusMessage = "Vector Paths Merged"
                    data.copy(points = mergedPoints) // Internal return
                    val newVector = cel.vectorData.copy(strokes = newStrokes)
                    updateLayer(layer.id) { l ->
                        l.copy(cels = l.cels + (currentFrame to cel.copy(vectorData = newVector)))
                    }
                }
            }
            
            if (!merged) {
                // If no merge, just snap start/end
                val startSnap = VectorMath.findSnapPoint(processedPoints.first().pos, existingPaths, threshold)
                val endSnap = VectorMath.findSnapPoint(processedPoints.last().pos, existingPaths, threshold)
                
                val newPoints = processedPoints.toMutableList()
                if (startSnap != null) newPoints[0] = newPoints[0].copy(pos = startSnap)
                if (endSnap != null) newPoints[newPoints.lastIndex] = newPoints.lastIndex.let { newPoints[it].copy(pos = endSnap) }
                processedPoints = newPoints
                data.copy(points = processedPoints)
            } else {
                null // Already updated in the merge block
            }
        } else {
            data.copy(points = processedPoints)
        }

        // 2. Post-Correction & Smart Shaping (Only if not already merged/updated)
        if (finalData != null) {
            var activePoints = finalData.points
            
            if (brush.postCorrection > 0f) {
                val epsilon = brush.postCorrection * 5f
                val simplifiedPos = VectorMath.simplify(activePoints.map { it.pos }, epsilon)
                activePoints = simplifiedPos.map { pos ->
                    activePoints.minByOrNull { it.pos.distanceTo(pos) }?.copy(pos = pos)
                        ?: com.inkframe.core.model.StrokePoint(pos, 0.5f, 0L)
                }
            }

            val finalProcessed = if (brush.smartShaping) {
                val shape = ShapeDetector.detect(activePoints.map { it.pos })
                if (shape.type != ShapeDetector.ShapeType.NONE) {
                    statusMessage = "Smart Shaped: ${shape.type.name}"
                    finalData.copy(points = shape.points.map { 
                        com.inkframe.core.model.StrokePoint(it, 0.5f, 0L) 
                    })
                } else finalData.copy(points = activePoints)
            } else finalData.copy(points = activePoints)

            val newVector = cel.vectorData.copy(strokes = cel.vectorData.strokes + finalProcessed)
            
            // Memory Optimization: Prevent vector "pooling" by simplifying heavy cels
            val optimizedVector = if (newVector.strokes.sumOf { it.points.size } > 5000) {
                newVector.copy(strokes = newVector.strokes.map { s ->
                    val simplified = VectorMath.simplify(s.points.map { it.pos }, 1.0f)
                    s.copy(points = simplified.map { pos ->
                        s.points.minBy { it.pos.distanceTo(pos) }.copy(pos = pos)
                    })
                })
            } else newVector

            updateLayer(layer.id) { l ->
                l.copy(cels = l.cels + (currentFrame to cel.copy(vectorData = optimizedVector)))
            }
        }
        
        onUiInvalidate?.invoke()
    }
    /** Records a finished vector stroke into the model for the active cel. */
    /** Finds the node closest to [pos] within [threshold] pixels. */
    fun findNodeAt(pos: Vec2, threshold: Float): Pair<Int, Int>? {
        val cel = activeLayer.cels[currentFrame] ?: return null
        val strokes = cel.vectorData.strokes
        for (si in strokes.indices) {
            for (pi in strokes[si].points.indices) {
                if (strokes[si].points[pi].pos.distanceTo(pos) < threshold) {
                    return Pair(si, pi)
                }
            }
        }
        return null
    }

    /** Moves the currently active sculpt node to [newPos]. */
    fun moveActiveNode(newPos: Vec2) {
        val (si, pi) = activeSculptNode ?: return
        val layer = activeLayer
        val cel = layer.cels[currentFrame] ?: return
        
        var targetPos = newPos

        // Perspective Grid Snapping
        if (perspectiveEnabled) {
            val uv = Vec2(targetPos.x / project.canvas.widthPx, targetPos.y / project.canvas.heightPx)
            val normC = uv * 2f - Vec2(1f, 1f)
            val d = normC.length()
            val gridC = normC * (1f + perspectiveFisheye * d * d)
            
            // Snap gridC to nearest 0.2 increment (matches 5x5 grid)
            val snappedGridC = Vec2(
                Math.round(gridC.x * 5f) / 5f,
                Math.round(gridC.y * 5f) / 5f
            )
            
            // Inverse the fisheye (approximation)
            val sd = snappedGridC.length()
            val invFisheye = 1f / (1f + perspectiveFisheye * sd * sd)
            val snappedNormC = snappedGridC * invFisheye
            
            val snappedUv = (snappedNormC + Vec2(1f, 1f)) * 0.5f
            val snappedPos = Vec2(snappedUv.x * project.canvas.widthPx, snappedUv.y * project.canvas.heightPx)
            
            // Only snap if very close to a grid intersection
            if (targetPos.distanceTo(snappedPos) < 15f / (zoomPercent / 100f)) {
                targetPos = snappedPos
                statusMessage = "Snapped to Perspective"
            }
        }

        val strokes = cel.vectorData.strokes.toMutableList()
        val points = strokes[si].points.toMutableList()
        val delta = newPos - points[pi].pos
        
        // Apply live with Merging logic
        if (shiftPressed) {
            for (i in points.indices) {
                val dist = Math.abs(i - pi).toFloat()
                val influence = Math.exp(-dist * dist / 32.0).toFloat()
                if (influence > 0.01f) {
                    points[i] = points[i].copy(pos = points[i].pos + delta * influence)
                }
            }
        } else {
            points[pi] = points[pi].copy(pos = newPos)
            
            // Node Merging: snap to neighbors
            if (pi > 0 && points[pi].pos.distanceTo(points[pi-1].pos) < 8f) {
                points.removeAt(pi)
                activeSculptNode = Pair(si, pi - 1)
                statusMessage = "Quantum Nodes Merged"
            } else if (pi < points.size - 1 && points[pi].pos.distanceTo(points[pi+1].pos) < 8f) {
                points.removeAt(pi)
                statusMessage = "Quantum Nodes Merged"
            }
        }
        
        strokes[si] = strokes[si].copy(points = points)
        val newVector = cel.vectorData.copy(strokes = strokes)
        updateLayer(layer.id) { l ->
            l.copy(cels = l.cels + (currentFrame to cel.copy(vectorData = newVector)))
        }
    }

    /** Finds all nodes within the [lassoPath] and adds them to [selectedNodes]. */
    fun selectNodesInLasso() {
        val cel = activeLayer.cels[currentFrame] ?: return
        val strokes = cel.vectorData.strokes
        selectedNodes.clear()
        for (si in strokes.indices) {
            for (pi in strokes[si].points.indices) {
                if (VectorMath.isPointInPolygon(strokes[si].points[pi].pos, lassoPath)) {
                    selectedNodes.add(Pair(si, pi))
                }
            }
        }
        lassoPath.clear()
    }

    /** Moves all [selectedNodes] by [delta]. */
    fun moveSelectedNodes(delta: Vec2) {
        val layer = activeLayer
        val cel = layer.cels[currentFrame] ?: return
        val strokes = cel.vectorData.strokes.toMutableList()
        
        // Group by stroke to avoid redundant model updates
        val byStroke = selectedNodes.groupBy { it.first }
        for ((si, nodeIndices) in byStroke) {
            val points = strokes[si].points.toMutableList()
            for (node in nodeIndices) {
                val pi = node.second
                points[pi] = points[pi].copy(pos = points[pi].pos + delta)
            }
            strokes[si] = strokes[si].copy(points = points)
        }
        
        val newVector = cel.vectorData.copy(strokes = strokes)
        updateLayer(layer.id) { l ->
            l.copy(cels = l.cels + (currentFrame to cel.copy(vectorData = newVector)))
        }
    }

    private fun updateScene(transform: (Scene) -> Scene) {
        project = project.copy(
            scenes = project.scenes.map { if (it.id == scene.id) transform(it) else it },
            modifiedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private companion object {
        fun newProject(): Project {
            val layer = Layer(name = "Layer 1")
            val scene = Scene(name = "Scene 1", frameCount = 24, layers = listOf(layer))
            return Project(
                name = "Untitled",
                canvas = CanvasSpec(widthPx = 1280, heightPx = 720, fps = 24),
                scenes = listOf(scene),
            )
        }
    }
}
