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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun StudioScreen(state: StudioState = viewModel()) {
    var canvasView by remember { mutableStateOf<CanvasView?>(null) }
    val context = LocalContext.current

    // Launchers (SAF)
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MediaTypes.DocumentKind.PROJECT.mimeType),
    ) { uri -> /* logic in StudioState */ }
    
    val onSave = { saveLauncher.launch(MediaTypes.suggestedFileName(state.project.name, MediaTypes.DocumentKind.PROJECT)) }
    val onOpen = { /* logic in StudioState */ }

    // Dialogs
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
        // Left Rail: Brushes + Modifiers
        Column(
            Modifier
                .width(64.dp)
                .fillMaxHeight()
                .background(ComposeBrush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent)))
        ) {
            BrushRail(state, onSelect = { state.brush = it })
            Spacer(Modifier.weight(1f))
            ModifierRail(state)
        }

        // Central Canvas Area
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
                        view.setShowChecker(state.showChecker)
                    }
                )
                TopToolbar(
                    state = state, 
                    onUndo = { canvasView?.undo() }, 
                    onRedo = { canvasView?.redo() }, 
                    onSave = onSave, 
                    onOpen = onOpen, 
                    onExport = { /* Export logic */ }, 
                    onFit = { canvasView?.fitToScreen() }, 
                    onReset100 = { canvasView?.resetZoom() },
                    onToggleOnion = { state.onionSkin = state.onionSkin.copy(enabled = !state.onionSkin.enabled); canvasView?.requestRender() },
                    onOpenOnionSettings = { state.showOnionSettings = true },
                    onToggleEyedropper = { state.eyedropperActive = !state.eyedropperActive },
                    onToggleFill = { state.fillActive = !state.fillActive },
                    onToggleChecker = { state.showChecker = !state.showChecker; canvasView?.setShowChecker(state.showChecker) }
                )
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

@Composable
fun InkFrameTitle(name: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(start = 12.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp).border(1.5.dp, ComposeBrush.sweepGradient(listOf(Color.Cyan, Color.Transparent, Color.Cyan)), CircleShape)) {
            Text("I", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.offset(x = (-2).dp))
            Text("F", color = Color.Cyan, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.offset(x = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("INKFRAME", color = Color.White, style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 4.sp, shadow = Shadow(color = Color.Cyan.copy(alpha = 0.5f), blurRadius = 8f)))
            Text(name.uppercase(), color = Color.White.copy(alpha = 0.4f), style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.sp))
        }
    }
}

@Composable
fun DonutIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, active: Boolean = false, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp).clip(CircleShape).background(ComposeBrush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent))).border(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f), CircleShape).clickableNoRipple(onClick)) {
        Icon(icon, contentDescription, tint = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
    }
}

@Composable
fun TopToolbar(state: StudioState, onUndo: () -> Unit, onRedo: () -> Unit, onSave: () -> Unit, onOpen: () -> Unit, onExport: () -> Unit, onFit: () -> Unit, onReset100: () -> Unit, onToggleOnion: () -> Unit, onOpenOnionSettings: () -> Unit, onToggleEyedropper: () -> Unit, onToggleFill: () -> Unit, onToggleChecker: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(ComposeBrush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DonutIconButton(Icons.Filled.FolderOpen, "Open", onClick = onOpen)
        DonutIconButton(Icons.Filled.Save, "Save", onClick = onSave)
        DonutIconButton(Icons.Filled.Movie, "Export", onClick = onExport)
        DonutIconButton(Icons.AutoMirrored.Filled.Undo, "Undo", state.canUndo, onUndo)
        DonutIconButton(Icons.AutoMirrored.Filled.Redo, "Redo", state.canRedo, onRedo)
        InkFrameTitle(state.statusMessage ?: state.project.name, Modifier.weight(1f))
        Text("${state.zoomPercent}%", color = Color.White, modifier = Modifier.clickableNoRipple(onReset100).padding(horizontal = 6.dp))
        DonutIconButton(Icons.Filled.FitScreen, "Fit", onClick = onFit)
        DonutIconButton(Icons.Filled.Colorize, "Picker", state.eyedropperActive, onToggleEyedropper)
        DonutIconButton(Icons.Filled.FormatColorFill, "Fill", state.fillActive, onToggleFill)
        DonutIconButton(Icons.Filled.Gesture, "Sculpt", state.sculptMode, { state.sculptMode = !state.sculptMode })
        DonutIconButton(Icons.Filled.Layers, "Onion", state.onionSkin.enabled, onToggleOnion)
        DonutIconButton(Icons.Filled.Tune, "Onion Settings", onClick = onOpenOnionSettings)
        DonutIconButton(Icons.Filled.GridOn, "Checker", state.showChecker, onToggleChecker)
        DonutIconButton(Icons.Filled.Repeat, "Symmetry", state.symmetryEnabled, { state.symmetryEnabled = !state.symmetryEnabled })
        DonutIconButton(Icons.Filled.Grid3x3, "Grid", state.perspectiveEnabled, { state.perspectiveEnabled = !state.perspectiveEnabled })
        DonutIconButton(Icons.Filled.Troubleshoot, "Telemetry", state.showVfxHud, { state.showVfxHud = !state.showVfxHud })
    }
}

@Composable
fun ModifierRail(state: StudioState) {
    Column(Modifier.width(60.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        ModifierKeyButton("SHIFT", state.shiftPressed, { state.shiftPressed = !state.shiftPressed })
        ModifierKeyButton("ALT", state.altPressed, { state.altPressed = !state.altPressed })
        ModifierKeyButton("CTRL", state.ctrlPressed, { state.ctrlPressed = !state.ctrlPressed })
    }
}

@Composable
fun ModifierKeyButton(label: String, active: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp).clip(CircleShape).background(if (active) ComposeBrush.radialGradient(listOf(Color.Cyan.copy(alpha = 0.3f), Color.Transparent)) else Color.Transparent).border(if (active) 2.dp else 1.dp, if (active) Color.Cyan else Color.White.copy(alpha = 0.1f), CircleShape).clickableNoRipple(onClick)) {
        Text(label.take(1), color = if (active) Color.Cyan else Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Normal)
    }
}

@Composable
fun BrushRail(state: StudioState, onSelect: (Brush) -> Unit) {
    Column(Modifier.width(64.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DefaultBrushes.all.forEach { b ->
            val selected = b.id == state.brush.id
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp).clip(CircleShape).background(if (selected) ComposeBrush.radialGradient(listOf(Color.Cyan.copy(alpha = 0.2f), Color.Transparent)) else Color.Transparent).border(if (selected) 2.dp else 1.dp, if (selected) Color.Cyan else Color.White.copy(alpha = 0.15f), CircleShape).clickableNoRipple { onSelect(b) }) {
                Text(b.name.take(1).uppercase(), color = if (selected) Color.Cyan else Color.White.copy(alpha = 0.7f), fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal, fontSize = 16.sp)
            }
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape).clickableNoRipple { state.showBrushLibrary = true }) {
            Icon(Icons.Filled.Add, "More", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun VfxHud(state: StudioState) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopEnd) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.background(Color.Black.copy(alpha = 0.2f), MaterialTheme.shapes.small).padding(8.dp)) {
            Text("TELEMETRY_V1.0", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("ZOOM: ${state.zoomPercent}%", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
            if (state.ctrlPressed) Text("MODE: LASSO", color = Color.Yellow, fontSize = 9.sp)
        }
    }
}

@Composable
fun FrameStripForLayer(state: StudioState, layer: Layer, active: Boolean, onFrame: (Int) -> Unit, onMoved: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (f in 0 until state.scene.frameCount) {
            val isCurrent = f == state.currentFrame
            val hasCel = layer.cels.containsKey(f)
            Box(Modifier.size(14.dp, 20.dp).clip(MaterialTheme.shapes.extraSmall).background(when { isCurrent && active -> MaterialTheme.colorScheme.primary; isCurrent -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f); hasCel -> Color(0xFF4A4A52); else -> Color(0xFF333339) }.let { if (f in state.scene.playbackRange) it else it.copy(alpha = 0.2f) }).clickableNoRipple { onFrame(f) })
        }
    }
}
