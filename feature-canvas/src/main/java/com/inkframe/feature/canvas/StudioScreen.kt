package com.inkframe.feature.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkframe.core.common.Vec2
import com.inkframe.core.model.*
import com.inkframe.engine.gl.CanvasRenderer
import androidx.compose.ui.graphics.Brush as ComposeBrush

@Composable
fun StudioScreen(state: StudioState = viewModel()) {
    var canvasView by remember { mutableStateOf<CanvasView?>(null) }
    val context = LocalContext.current

    if (state.showBrushLibrary) {
        BrushLibraryDialog(onSelect = { state.brush = it; state.showBrushLibrary = false }, onDismiss = { state.showBrushLibrary = false })
    }
    
    if (state.renamingLayerId != null) {
        val layer = state.scene.layerById(state.renamingLayerId!!)
        if (layer != null) {
            RenameLayerDialog(layer.name, onConfirm = { state.renameLayer(layer.id, it) }, onDismiss = { state.renamingLayerId = null })
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(ComposeBrush.verticalGradient(listOf(Color(0xFF0F0F12), Color(0xFF1A1A20), Color(0xFF0A0A0C))))
    ) {
        Column(Modifier.width(64.dp).fillMaxHeight().background(ComposeBrush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent)))) {
            BrushRail(state, onSelect = { state.brush = it })
            Spacer(Modifier.weight(1f))
            ModifierRail(state)
        }

        Column(Modifier.weight(1f)) {
            Box(Modifier.weight(1f)) {
                if (state.showVfxHud) { VfxHud(state) }
                AndroidView(
                    factory = { ctx ->
                        CanvasView(
                            context = ctx,
                            canvasWidth = state.project.canvas.widthPx,
                            canvasHeight = state.project.canvas.heightPx,
                            sceneProvider = { state.buildDrawList() },
                            sculptProvider = { if (state.sculptMode) state.activeLayer.cels[state.currentFrame]?.vectorData?.strokes ?: emptyList() else emptyList() },
                            perspectiveProvider = { CanvasRenderer.PerspectiveConfig(state.perspectiveEnabled, state.perspectiveFisheye) },
                            lassoProvider = { state.lassoPath },
                            selectionProvider = { state.selectedNodes },
                            cursorProvider = { state.cursorPosition },
                            strokeConfig = {
                                val sid = state.ensureActiveCel()
                                CanvasView.StrokeConfig(sid, state.brush, state.color, state.shiftPressed, state.altPressed, state.ctrlPressed)
                            },
                            onEngineReady = { engine -> state.bindEngine(engine) },
                        ).also { view ->
                            canvasView = view
                            view.onStrokeFinished = { state.recordStroke(it) }
                            view.sculptListener = object : CanvasView.SculptListener {
                                override fun onNodeBegin(pos: Vec2) = state.findNodeAt(pos, 24f / (state.zoomPercent / 100f))?.also { state.activeSculptNode = it } != null
                                override fun onNodeMove(pos: Vec2) = state.moveActiveNode(pos)
                                override fun onNodeEnd() { state.activeSculptNode = null }
                                override fun onLassoBegin(pos: Vec2) { state.lassoPath.clear(); state.lassoPath.add(pos) }
                                override fun onLassoMove(pos: Vec2) { state.lassoPath.add(pos) }
                                override fun onLassoEnd() { state.selectNodesInLasso() }
                                override fun onCursorMove(pos: Vec2) {
                                    state.cursorPosition = pos
                                    if (state.sculptMode) state.hoveredNode = state.findNodeAt(pos, 30f / (state.zoomPercent / 100f))
                                }
                            }
                            state.onUiInvalidate = { view.requestRender() }
                            view.onViewportChanged = { scale -> view.post { state.setZoom(scale) } }
                            state.postEngineWork = { block -> view.runOnEngine(block) }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.sculptActive = state.sculptMode
                        view.ctrlActive = state.ctrlPressed
                        view.symmetryEnabled = state.symmetryEnabled
                        view.symmetryCount = state.symmetryCount
                        view.setShowChecker(state.showChecker)
                    }
                )
                TopToolbar(state, onUndo = { canvasView?.undo() }, onRedo = { canvasView?.redo() }, onSave = {}, onOpen = { }, 
                           onExport = { }, onFit = { canvasView?.fitToScreen() }, onReset100 = { canvasView?.resetZoom() },
                           onToggleOnion = { state.onionSkin = state.onionSkin.copy(enabled = !state.onionSkin.enabled); canvasView?.requestRender() },
                           onOpenOnionSettings = { state.showOnionSettings = true },
                           onToggleEyedropper = { state.eyedropperActive = !state.eyedropperActive },
                           onToggleFill = { state.fillActive = !state.fillActive },
                           onToggleChecker = { state.showChecker = !state.showChecker; canvasView?.setShowChecker(state.showChecker) })
            }
            FibonacciTimeline(state, onChanged = { canvasView?.requestRender() })
        }
        SidePanel(state, onChanged = { canvasView?.requestRender() })
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> canvasView?.onPause()
                Lifecycle.Event.ON_RESUME -> canvasView?.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
