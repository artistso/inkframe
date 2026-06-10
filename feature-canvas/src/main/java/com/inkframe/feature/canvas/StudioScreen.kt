package com.inkframe.feature.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush as ComposeBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.text.font.FontWeight
import com.inkframe.core.model.BrushAdjustments
import com.inkframe.core.model.OnionSkinSettings
import com.inkframe.core.model.TimelineDrag
import com.inkframe.core.model.Hsv
import com.inkframe.core.model.RgbaColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkframe.core.model.BlendMode
import com.inkframe.core.model.Brush
import com.inkframe.core.model.DefaultBrushes
import com.inkframe.core.model.ExportPlanner
import com.inkframe.core.model.MediaTypes
import kotlinx.coroutines.delay

/**
 * The top-level studio UI: a left tool rail (brushes), the central GL canvas, a right
 * panel (layers + color), and a bottom timeline with playback + onion skin toggle.
 */
@Composable
fun StudioScreen(state: StudioState = viewModel()) {
    var canvasView by remember { mutableStateOf<CanvasView?>(null) }
    val context = LocalContext.current
    val resolver = context.contentResolver

    // --- Save the project via SAF (system "create document" picker) ---
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MediaTypes.DocumentKind.PROJECT.mimeType),
    ) saveResult@{ uri ->
        val view = canvasView ?: return@saveResult
        if (uri == null) { state.statusMessage = "Save cancelled"; return@saveResult }
        val snapshot = state.project
        state.setBusy(true)
        val out = runCatching { resolver.openOutputStream(uri) }.getOrNull()
        if (out == null) { state.setBusy(false); state.statusMessage = "Couldn't open destination"; return@saveResult }
        view.saveProjectTo(snapshot, out) { result ->
            view.post {
                state.setBusy(false)
                state.statusMessage = result.fold(
                    onSuccess = { "Saved \u201c${snapshot.name}\u201d" },
                    onFailure = { "Save failed: ${it.message}" },
                )
            }
        }
    }

    // --- Open a project via SAF ("open document" picker) ---
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) openResult@{ uri ->
        val view = canvasView ?: return@openResult
        if (uri == null) { state.statusMessage = "Open cancelled"; return@openResult }
        state.setBusy(true)
        val input = runCatching { resolver.openInputStream(uri) }.getOrNull()
        if (input == null) { state.setBusy(false); state.statusMessage = "Couldn't open file"; return@openResult }
        view.loadProjectFrom(input) { result ->
            view.post {
                state.setBusy(false)
                result.fold(
                    onSuccess = { loaded ->
                        state.replaceProject(loaded)
                        view.requestRender()
                        state.statusMessage = "Opened \u201c${loaded.name}\u201d"
                    },
                    onFailure = { state.statusMessage = "Open failed: ${it.message}" },
                )
            }
        }
    }

    val onSave: () -> Unit = {
        saveLauncher.launch(MediaTypes.suggestedFileName(state.project.name, MediaTypes.DocumentKind.PROJECT))
    }
    val onOpen: () -> Unit = { openLauncher.launch(MediaTypes.PROJECT_OPEN_MIME_TYPES) }

    // --- Export via SAF ---------------------------------------------------
    var showExportDialog by remember { mutableStateOf(false) }
    // The format chosen in the dialog, remembered until the SAF picker returns a Uri.
    var pendingExportFormat by remember { mutableStateOf<ExportManager.ExportFormat?>(null) }

    fun runExport(format: ExportManager.ExportFormat, uri: android.net.Uri) {
        val view = canvasView ?: return
        val plan = ExportPlanner.plan(state.scene, state.project.canvas, ExportPlanner.Range.PLAYBACK)
        state.setBusy(true)
        state.statusMessage = "Exporting\u2026 0/${plan.frameCount}"
        val onDone: (Result<Unit>) -> Unit = { result ->
            view.post {
                state.setBusy(false)
                state.statusMessage = result.fold(
                    onSuccess = { "Exported \u201c${state.project.name}\u201d (${plan.frameCount} frames)" },
                    onFailure = { "Export failed: ${it.message}" },
                )
            }
        }
        val onProg: (Int, Int) -> Unit = { d, t -> view.post { state.statusMessage = "Exporting\u2026 $d/$t" } }
        if (format == ExportManager.ExportFormat.MP4) {
            // MediaMuxer needs a seekable fd; "rw" guarantees seekability.
            val pfd = runCatching { resolver.openFileDescriptor(uri, "rw") }.getOrNull()
            if (pfd == null) { state.setBusy(false); state.statusMessage = "Couldn't open destination"; return }
            view.exportAnimationTo(plan, format, out = null, fd = pfd.fileDescriptor,
                drawListFor = { f -> state.buildExportDrawList(f) }, onProgress = onProg) { r ->
                runCatching { pfd.close() }
                onDone(r)
            }
        } else {
            val out = runCatching { resolver.openOutputStream(uri) }.getOrNull()
            if (out == null) { state.setBusy(false); state.statusMessage = "Couldn't open destination"; return }
            view.exportAnimationTo(plan, format, out = out, fd = null,
                drawListFor = { f -> state.buildExportDrawList(f) }, onProgress = onProg, onResult = onDone)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) exportResult@{ uri ->
        val format = pendingExportFormat
        pendingExportFormat = null
        if (uri == null) { state.statusMessage = "Export cancelled"; return@exportResult }
        if (format != null) runExport(format, uri)
    }

    val doExport: (ExportManager.ExportFormat) -> Unit = { format ->
        showExportDialog = false
        pendingExportFormat = format
        val kind = when (format) {
            ExportManager.ExportFormat.MP4 -> MediaTypes.DocumentKind.MP4
            ExportManager.ExportFormat.GIF -> MediaTypes.DocumentKind.GIF
            ExportManager.ExportFormat.PNG_SEQUENCE -> MediaTypes.DocumentKind.PNG_SEQUENCE
        }
        exportLauncher.launch(MediaTypes.suggestedFileName(state.project.name, kind))
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onMp4 = { doExport(ExportManager.ExportFormat.MP4) },
            onGif = { doExport(ExportManager.ExportFormat.GIF) },
            onPngSequence = { doExport(ExportManager.ExportFormat.PNG_SEQUENCE) },
        )
    }

    // Playback loop: advances frames at the project FPS while playing.
    LaunchedEffect(state.isPlaying, state.project.canvas.fps) {
        if (state.isPlaying) {
            while (state.isPlaying) {
                delay(state.frameDurationMs)
                state.advancePlayback()
                canvasView?.requestRender()
            }
        }
    }

    if (state.showBrushSettings) {
        BrushSettingsPanel(
            brush = state.brush,
            onChange = { transform -> state.updateBrush(transform) },
            onReset = { state.updateBrush { BrushAdjustments.resetToDefault(it) } },
            onDismiss = { state.showBrushSettings = false },
        )
    }

    if (state.showBrushLibrary) {
        BrushLibraryDialog(
            onSelect = { state.brush = it; state.showBrushLibrary = false },
            onDismiss = { state.showBrushLibrary = false }
        )
    }

    if (state.showOnionSettings) {
        OnionSettingsPanel(
            settings = state.onionSkin,
            onChange = { state.onionSkin = it; canvasView?.requestRender() },
            onDismiss = { state.showOnionSettings = false },
        )
    }

    if (state.showColorPicker) {
        ColorPickerDialog(
            initial = state.color,
            onConfirm = { picked -> state.commitColor(picked); canvasView?.requestRender() },
            onDismiss = { state.showColorPicker = false },
        )
    }

    val renamingId = state.renamingLayerId
    if (renamingId != null) {
        val layer = state.scene.layerById(renamingId)
        if (layer != null) {
            RenameLayerDialog(
                currentName = layer.name,
                onConfirm = { name -> state.renameLayer(renamingId, name) },
                onDismiss = { state.renamingLayerId = null },
            )
        } else {
            state.renamingLayerId = null
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(
                ComposeBrush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F12), // Deep Space Top
                        Color(0xFF1A1A20), // Horizon Line
                        Color(0xFF0A0A0C)  // Deep Ink Bottom
                    )
                )
            )
    ) {
        // Left side: Combined Brush and Modifier Rail
        Column(
            Modifier
                .width(64.dp)
                .fillMaxHeight()
                .background(
                    ComposeBrush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
        ) {
            BrushRail(
                state = state,
                onSelect = { state.brush = it },
                modifier = Modifier,
            )
            
            Spacer(Modifier.weight(1f))
            
            ModifierRail(state)
            Spacer(Modifier.height(8.dp))
        }

        Column(Modifier.weight(1f)) {
            TopToolbar(
                state = state,
                onUndo = { canvasView?.undo() },
                onRedo = { canvasView?.redo() },
                onSave = onSave,
                onOpen = onOpen,
                onExport = { showExportDialog = true },
                onFit = { canvasView?.fitToScreen() },
                onReset100 = { canvasView?.resetZoom() },
                onToggleOnion = {
                    state.onionSkin = state.onionSkin.copy(enabled = !state.onionSkin.enabled)
                    canvasView?.requestRender()
                },
                onOpenOnionSettings = { state.showOnionSettings = true },
                onToggleEyedropper = {
                    state.eyedropperActive = !state.eyedropperActive
                    state.fillActive = false                       // tools are mutually exclusive
                    canvasView?.eyedropperActive = state.eyedropperActive
                    canvasView?.fillActive = false
                    state.statusMessage = if (state.eyedropperActive) "Eyedropper: tap the canvas" else null
                },
                onToggleFill = {
                    state.fillActive = !state.fillActive
                    state.eyedropperActive = false
                    canvasView?.fillActive = state.fillActive
                    canvasView?.eyedropperActive = false
                    state.statusMessage = if (state.fillActive) "Fill: tap an area" else null
                },
                onToggleChecker = {
                    state.showChecker = !state.showChecker
                    canvasView?.setShowChecker(state.showChecker)
                },
            )
            Box(Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        CanvasView(
                if (state.showVfxHud) {
                    VfxHud(state)
                }
                            context = ctx,
                            canvasWidth = state.project.canvas.widthPx,
                            canvasHeight = state.project.canvas.heightPx,
                            sceneProvider = { state.buildDrawList() },
                            sculptProvider = {
                                if (state.sculptMode) {
                                    state.activeLayer.cels[state.currentFrame]?.vectorData?.strokes ?: emptyList()
                                } else emptyList()
                            },
                            perspectiveProvider = {
                                CanvasRenderer.PerspectiveConfig(state.perspectiveEnabled, state.perspectiveFisheye)
                            },
                            lassoProvider = { state.lassoPath },
                            selectionProvider = { state.selectedNodes },
                            cursorProvider = { state.cursorPosition },
                            strokeConfig = {
                                val sid = state.ensureActiveCel()
                                CanvasView.StrokeConfig(
                                    sid, state.brush, state.color,
                                    shiftPressed = state.shiftPressed,
                                    altPressed = state.altPressed,
                                    ctrlPressed = state.ctrlPressed
                                )
                            },
                            onEngineReady = { engine -> state.bindEngine(engine) },
                        ).also { view ->
                            canvasView = view
                            view.onStrokeFinished = { state.recordStroke(it) }
                            view.sculptListener = object : CanvasView.SculptListener {
                                override fun onNodeBegin(pos: Vec2): Boolean {
                                    val node = state.findNodeAt(pos, 24f / state.zoomPercent * 100f)
                                    if (node != null) {
                                        state.activeSculptNode = node
                                        return true
                                    }
                                    return false
                                }
                                override fun onNodeMove(pos: Vec2) {
                                    state.moveActiveNode(pos)
                                }
                                override fun onNodeEnd() {
                                    state.activeSculptNode = null
                                }
                                override fun onLassoBegin(pos: Vec2) {
                                    state.lassoPath.clear()
                                    state.lassoPath.add(pos)
                                }
                                override fun onLassoMove(pos: Vec2) {
                                    state.lassoPath.add(pos)
                                }
                                override fun onLassoEnd() {
                                    state.selectNodesInLasso()
                                }
                            }
                            // Engine history callbacks fire on the GL thread; bounce a
                            // redraw request back so the toolbar reflects new state.
                            state.onUiInvalidate = { view.requestRender() }
                            // Reflect pan/zoom changes in the toolbar zoom indicator.
                            view.onViewportChanged = { scale -> view.post { state.setZoom(scale) } }
                            // Route timeline duplicate/paste GPU clones onto the GL thread.
                            state.postEngineWork = { block -> view.runOnEngine(block) }
                            // After GL-context loss + restore, redraw with recovered art.
                            view.onContextRestored = {
                                view.requestRender()
                                state.statusMessage = "Restored after display reset"
                            }
                            // Eyedropper: arm state -> view, and feed sampled colour back.
                            view.eyedropperActive = state.eyedropperActive
                            view.sculptActive = state.sculptMode
                            view.onColorSampled = { sampled ->
                                state.eyedropperActive = false   // one-shot: disarm after a pick
                                view.eyedropperActive = false
                                if (sampled != null) {
                                    state.commitColor(sampled.withAlpha(1f))
                                    state.statusMessage = "Picked #${"%08X".format(sampled.toArgb())}"
                                } else {
                                    state.statusMessage = "Nothing to pick there"
                                }
                            }
                            // Bucket: arm state -> view, report result.
                            view.fillActive = state.fillActive
                            view.onFilled = { changed ->
                                state.fillActive = false
                                view.fillActive = false
                                state.statusMessage = if (changed) "Filled" else "Nothing to fill there"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.eyedropperActive = state.eyedropperActive
                        view.fillActive = state.fillActive
                        view.sculptActive = state.sculptMode
                        view.ctrlActive = state.ctrlPressed
                        view.shiftActive = state.shiftPressed
                        view.altActive = state.altPressed
                        view.setShowChecker(state.showChecker)
                    }
                )
            }
            // Fibonacci Timeline Shell
            Box(Modifier.fillMaxWidth()) {
                FibonacciTimeline(state, onChanged = { canvasView?.requestRender() })
            }
        }

        SidePanel(state = state, onChanged = { canvasView?.requestRender() })
    }

    // Forward Activity lifecycle to the GL view so it can pause/resume rendering and back
    // up artwork before the EGL context may be destroyed.
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.stop()
        }
    }
}

/**
 * A "Donut" styled icon button with a transparent center and thin ring.
 * Part of the organic UI system to maximize canvas visibility.
 */
@Composable
private fun InkFrameTitle(
    name: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(start = 12.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stylized "IF" Logo Ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .border(1.5.dp, ComposeBrush.sweepGradient(listOf(Color.Cyan, Color.Transparent, Color.Cyan)), CircleShape)
        ) {
            Text(
                "I", 
                color = Color.White, 
                fontWeight = FontWeight.Black, 
                fontSize = 14.sp,
                modifier = Modifier.offset(x = (-2).dp)
            )
            Text(
                "F", 
                color = Color.Cyan, 
                fontWeight = FontWeight.Black, 
                fontSize = 14.sp,
                modifier = Modifier.offset(x = 2.dp)
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        // The Project Name with "Ink Frame" stylized look
        Column {
            Text(
                text = "INKFRAME",
                color = Color.White,
                style = TextStyle(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 4.sp,
                    shadow = Shadow(color = Color.Cyan.copy(alpha = 0.5f), blurRadius = 8f)
                )
            )
            Text(
                text = name.uppercase(),
                color = Color.White.copy(alpha = 0.4f),
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

@Composable
private fun DonutIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                ComposeBrush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickableNoRipple(onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun VfxHud(state: StudioState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.2f), MaterialTheme.shapes.small)
                .padding(8.dp)
        ) {
            Text(
                "TELEMETRY_V1.0",
                color = Color.Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "ZOOM: ${state.zoomPercent}%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                "NODES: ${state.activeLayer.cels[state.currentFrame]?.vectorData?.strokes?.sumOf { it.points.size } ?: 0}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            if (state.ctrlPressed) {
                Text("MODE: LASSO_ENGAGED", color = Color.Yellow, fontSize = 9.sp)
            }
            if (state.shiftPressed) {
                Text("MODE: SEGMENT_SCULPT", color = Color.Magenta, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun TopToolbar(
    state: StudioState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onFit: () -> Unit,
    onReset100: () -> Unit,
    onToggleOnion: () -> Unit,
    onOpenOnionSettings: () -> Unit,
    onToggleEyedropper: () -> Unit,
    onToggleFill: () -> Unit,
    onToggleChecker: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                ComposeBrush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                )
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DonutIconButton(icon = Icons.Filled.FolderOpen, contentDescription = "Open", onClick = onOpen)
        DonutIconButton(icon = Icons.Filled.Save, contentDescription = "Save", onClick = onSave)
        DonutIconButton(icon = Icons.Filled.Movie, contentDescription = "Export animation", onClick = onExport)
        DonutIconButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "Undo",
            active = state.canUndo,
            onClick = onUndo
        )
        DonutIconButton(
            icon = Icons.AutoMirrored.Filled.Redo,
            contentDescription = "Redo",
            active = state.canRedo,
            onClick = onRedo
        )

        InkFrameTitle(
            name = state.statusMessage ?: state.project.name,
            modifier = Modifier.weight(1f)
        )

        // Zoom controls: tap the % to reset to 100%, the frame icon to fit.
        Text(
            "${state.zoomPercent}%",
            color = Color.White,
            modifier = Modifier
                .clickableNoRipple(onReset100)
                .padding(horizontal = 6.dp),
        )
        DonutIconButton(
            icon = Icons.Filled.FitScreen,
            contentDescription = "Fit to screen",
            onClick = onFit
        )
        Spacer(Modifier.width(4.dp))

        // Eyedropper: arm it, then tap the canvas to pick a colour.
        DonutIconButton(
            icon = Icons.Filled.Colorize,
            contentDescription = "Eyedropper",
            active = state.eyedropperActive,
            onClick = onToggleEyedropper
        )
        Spacer(Modifier.width(4.dp))

        // Bucket fill: arm it, then tap an area to flood-fill with the current colour.
        DonutIconButton(
            icon = Icons.Filled.FormatColorFill,
            contentDescription = "Fill",
            active = state.fillActive,
            onClick = onToggleFill
        )
        Spacer(Modifier.width(4.dp))

        // Sculpt Mode (Quantum Path Editing)
        DonutIconButton(
            icon = Icons.Filled.Gesture,
            contentDescription = "Sculpt Path",
            active = state.sculptMode,
            onClick = { state.sculptMode = !state.sculptMode; state.statusMessage = if (state.sculptMode) "Sculpt Mode: Drag points" else null }
        )
        Spacer(Modifier.width(4.dp))

        // Tap toggles onion skin; the adjacent gear opens its multi-frame settings.
        DonutIconButton(
            icon = Icons.Filled.Layers,
            contentDescription = "Onion skin",
            active = state.onionSkin.enabled,
            onClick = onToggleOnion
        )
        Spacer(Modifier.width(4.dp))

        DonutIconButton(
            icon = Icons.Filled.Tune,
            contentDescription = "Onion skin settings",
            onClick = onOpenOnionSettings
        )
        Spacer(Modifier.width(4.dp))

        DonutIconButton(
            icon = Icons.Filled.GridOn,
            contentDescription = "Transparency checker",
            active = state.showChecker,
            onClick = {
                state.showChecker = !state.showChecker
                onToggleChecker()
            }
        )
        Spacer(Modifier.width(4.dp))
        
        DonutIconButton(
            icon = Icons.Filled.Repeat,
            contentDescription = "Symmetry",
            active = state.symmetryEnabled,
            onClick = { state.symmetryEnabled = !state.symmetryEnabled }
        )
        Spacer(Modifier.width(4.dp))
        
        DonutIconButton(
            icon = Icons.Filled.Grid3x3,
            contentDescription = "Perspective Grid",
            active = state.perspectiveEnabled,
            onClick = { state.perspectiveEnabled = !state.perspectiveEnabled }
        )
        Spacer(Modifier.width(4.dp))
        
        DonutIconButton(
            icon = Icons.Filled.Troubleshoot,
            contentDescription = "VFX Telemetry",
            active = state.showVfxHud,
            onClick = { state.showVfxHud = !state.showVfxHud }
        )
    }
}

/**
 * A side rail containing desktop-style modifier keys (Ctrl, Alt, Shift).
 * These are "sticky" toggles that provide precision control on mobile.
 */
@Composable
private fun ModifierRail(state: StudioState) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        
        ModifierKeyButton(
            label = "SHIFT",
            active = state.shiftPressed,
            onClick = { state.shiftPressed = !state.shiftPressed }
        )
        ModifierKeyButton(
            label = "ALT",
            active = state.altPressed,
            onClick = { state.altPressed = !state.altPressed }
        )
        ModifierKeyButton(
            label = "CTRL",
            active = state.ctrlPressed,
            onClick = { state.ctrlPressed = !state.ctrlPressed }
        )
    }
}

@Composable
private fun ModifierKeyButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(
                if (active) ComposeBrush.radialGradient(listOf(Color.Cyan.copy(alpha = 0.3f), Color.Transparent))
                else Color.Transparent
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) Color.Cyan else Color.White.copy(alpha = 0.1f),
                shape = CircleShape
            )
            .clickableNoRipple(onClick)
    ) {
        Text(
            text = label.take(1),
            color = if (active) Color.Cyan else Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Normal
        )
    }
}

@Composable
private fun BrushRail(
    state: StudioState,
    onSelect: (Brush) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.brush
    Column(
        modifier
            .width(64.dp)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DefaultBrushes.all.forEach { b ->
            val selected = b.id == current.id
            val expanded = selected && state.brushSettingsExpanded
            
            Box(contentAlignment = Alignment.CenterStart) {
                // The Main Brush Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) ComposeBrush.radialGradient(listOf(Color.Cyan.copy(alpha = 0.2f), Color.Transparent))
                            else Color.Transparent
                        )
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) Color.Cyan else Color.White.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .clickableNoRipple { 
                            if (selected) state.brushSettingsExpanded = !state.brushSettingsExpanded
                            else {
                                onSelect(b)
                                state.brushSettingsExpanded = false
                            }
                        }
                ) {
                    Text(
                        text = b.name.take(1).uppercase(),
                        color = if (selected) Color.Cyan else Color.White.copy(alpha = 0.7f),
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
                        fontSize = 16.sp
                    )
                }

                // The Baked-in Sub-menu (Horizontal Fibonacci Bloom)
                if (expanded) {
                    Row(
                        modifier = Modifier
                            .padding(start = 56.dp)
                            .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                            .border(1.dp, Color.Cyan.copy(alpha = 0.2f), MaterialTheme.shapes.medium)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Compact integrated controls for the Pen
                        IntegratedSlider(
                            label = "Size", 
                            value = current.sizePx, 
                            range = BrushAdjustments.SIZE_RANGE,
                            onValueChange = { state.updateBrush { BrushAdjustments.withSize(it, it.sizePx + it.sizePx * 0.1f * (if (it.sizePx < 50) 1f else 0.5f)) } } // placeholder for gesture
                        )
                        
                        // We'll use a specific Pen tool focus here
                        if (current.id == "kjg_ink" || current.id == "ink") {
                            DonutIconButton(
                                icon = Icons.Filled.Gesture, 
                                contentDescription = "Taper", 
                                active = current.taperStart > 0f,
                                onClick = { state.updateBrush { it.copy(taperStart = if (it.taperStart > 0f) 0f else 0.2f) } }
                            )
                            DonutIconButton(
                                icon = Icons.Filled.Repeat, 
                                contentDescription = "Magnet", 
                                active = current.vectorMagnet > 0f,
                                onClick = { state.updateBrush { it.copy(vectorMagnet = if (it.vectorMagnet > 0f) 0f else 0.5f) } }
                            )
                        }
                    }
                }
            }
        }
        
        // Button to expand/add more brushes
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                .clickableNoRipple { state.showBrushLibrary = true }
        ) {
            Icon(Icons.Filled.Add, contentDescription = "More brushes", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun IntegratedSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        // A vertical "Donut" dial or simple text indicator that acts as a slider
        Text(
            "${value.toInt()}", 
            color = Color.White, 
            fontSize = 14.sp, 
            modifier = Modifier.pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onValueChange(dragAmount.y) // vertical drag to adjust
                }
            }
        )
    }
}

@Composable
private fun BrushSettingsPanel(
    brush: Brush,
    onChange: ((Brush) -> Brush) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Brush — ${brush.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LabeledSlider(
                    label = "Size", value = brush.sizePx, range = BrushAdjustments.SIZE_RANGE,
                    valueText = "${brush.sizePx.toInt()} px",
                ) { v -> onChange { BrushAdjustments.withSize(it, v) } }

                LabeledSlider(
                    label = "Min size", value = brush.minSizePx, range = BrushAdjustments.MIN_SIZE_RANGE,
                    valueText = "${brush.minSizePx.toInt()} px",
                ) { v -> onChange { BrushAdjustments.withMinSize(it, v) } }

                LabeledSlider(
                    label = "Opacity", value = brush.opacity, range = BrushAdjustments.OPACITY_RANGE,
                    valueText = percent(brush.opacity),
                ) { v -> onChange { BrushAdjustments.withOpacity(it, v) } }

                LabeledSlider(
                    label = "Flow", value = brush.flow, range = BrushAdjustments.FLOW_RANGE,
                    valueText = percent(brush.flow),
                ) { v -> onChange { BrushAdjustments.withFlow(it, v) } }

                LabeledSlider(
                    label = "Hardness", value = brush.hardness, range = BrushAdjustments.HARDNESS_RANGE,
                    valueText = percent(brush.hardness),
                ) { v -> onChange { BrushAdjustments.withHardness(it, v) } }

                LabeledSlider(
                    label = "Spacing", value = brush.spacing, range = BrushAdjustments.SPACING_RANGE,
                    valueText = percent(brush.spacing),
                ) { v -> onChange { BrushAdjustments.withSpacing(it, v) } }

                LabeledSlider(
                    label = "Smoothing", value = brush.smoothing, range = BrushAdjustments.SMOOTHING_RANGE,
                    valueText = percent(brush.smoothing),
                ) { v -> onChange { BrushAdjustments.withSmoothing(it, v) } }

                LabeledSlider(
                    label = "Stabilization", value = brush.stabilization, range = BrushAdjustments.STABILIZATION_RANGE,
                    valueText = percent(brush.stabilization),
                ) { v -> onChange { BrushAdjustments.withStabilization(it, v) } }

                LabeledSlider(
                    label = "Post-Correction (CSP)", value = brush.postCorrection, range = BrushAdjustments.POST_CORRECTION_RANGE,
                    valueText = percent(brush.postCorrection),
                ) { v -> onChange { BrushAdjustments.withPostCorrection(it, v) } }

                LabeledSlider(
                    label = "Vector Magnet", value = brush.vectorMagnet, range = BrushAdjustments.VECTOR_MAGNET_RANGE,
                    valueText = percent(brush.vectorMagnet),
                ) { v -> onChange { BrushAdjustments.withVectorMagnet(it, v) } }

                ToggleRow("Pressure → size", brush.pressureToSize) { e ->
                    onChange { BrushAdjustments.withPressureToSize(it, e) }
                }
                ToggleRow("Pressure → opacity", brush.pressureToOpacity) { e ->
                    onChange { BrushAdjustments.withPressureToOpacity(it, e) }
                }
                ToggleRow("Build-up (airbrush)", brush.buildUp) { e ->
                    onChange { BrushAdjustments.withBuildUp(it, e) }
                }
                ToggleRow("Glow Trail (Vector)", brush.glowTrail) { e ->
                    onChange { BrushAdjustments.withGlowTrail(it, e) }
                }
                ToggleRow("Smart Shaping", brush.smartShaping) { e ->
                    onChange { BrushAdjustments.withSmartShaping(it, e) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onReset) { Text("Reset") } },
    )
}

@Composable
private fun BrushLibraryDialog(
    onSelect: (Brush) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Brush Library") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select a brush type to add to your rail:")
                DefaultBrushes.all.forEach { b ->
                    TextButton(
                        onClick = { onSelect(b) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(b.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValue: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, modifier = Modifier.weight(1f))
            Text(valueText)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            valueRange = range.start..range.endInclusive,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun percent(v: Float): String = "${(v * 100f).toInt()}%"

@Composable
private fun OnionSettingsPanel(
    settings: OnionSkinSettings,
    onChange: (OnionSkinSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxR = OnionSkinSettings.MAX_RANGE.toFloat()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Onion skin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToggleRow("Enabled", settings.enabled) { onChange(settings.copy(enabled = it)) }
                LabeledSlider(
                    label = "Frames before", value = settings.framesBefore.toFloat(),
                    range = 0f..maxR, valueText = "${settings.framesBefore}",
                ) { v -> onChange(settings.copy(framesBefore = v.toInt())) }
                LabeledSlider(
                    label = "Frames after", value = settings.framesAfter.toFloat(),
                    range = 0f..maxR, valueText = "${settings.framesAfter}",
                ) { v -> onChange(settings.copy(framesAfter = v.toInt())) }
                LabeledSlider(
                    label = "Near opacity", value = settings.nearOpacity,
                    range = 0f..1f, valueText = percent(settings.nearOpacity),
                ) { v -> onChange(settings.copy(nearOpacity = v)) }
                LabeledSlider(
                    label = "Far opacity", value = settings.farOpacity,
                    range = 0f..1f, valueText = percent(settings.farOpacity),
                ) { v -> onChange(settings.copy(farOpacity = v)) }
                LabeledSlider(
                    label = "Tint strength", value = settings.tintStrength,
                    range = 0f..1f, valueText = percent(settings.tintStrength),
                ) { v -> onChange(settings.copy(tintStrength = v)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tints", modifier = Modifier.weight(1f))
                    SwatchDot(settings.beforeTint); Text(" before  ")
                    SwatchDot(settings.afterTint); Text(" after")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SwatchDot(color: RgbaColor) {
    Box(
        Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(Color(color.toArgb())),
    )
}

@Composable
private fun ColorPickerDialog(
    initial: RgbaColor,
    onConfirm: (RgbaColor) -> Unit,
    onDismiss: () -> Unit,
) {
    // Edit in HSV; seed from the incoming RGBA. Preserve the source alpha.
    var hsv by remember(initial) { mutableStateOf(Hsv.fromRgba(initial)) }
    val preview = hsv.toRgba()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Live preview swatch.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color(preview.toArgb())),
                )
                LabeledSlider(
                    label = "Hue", value = hsv.h, range = 0f..360f,
                    valueText = "${hsv.h.toInt()}\u00B0",
                ) { v -> hsv = hsv.withHue(v) }
                LabeledSlider(
                    label = "Saturation", value = hsv.s, range = 0f..1f,
                    valueText = percent(hsv.s),
                ) { v -> hsv = hsv.withSaturation(v) }
                LabeledSlider(
                    label = "Brightness", value = hsv.v, range = 0f..1f,
                    valueText = percent(hsv.v),
                ) { v -> hsv = hsv.withValue(v) }
                LabeledSlider(
                    label = "Alpha", value = hsv.a, range = 0f..1f,
                    valueText = percent(hsv.a),
                ) { v -> hsv = hsv.withAlpha(v) }
                Text("#${hexOf(preview)}", color = Color.White)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(preview); onDismiss() }) { Text("Select") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Uppercase ARGB hex without the leading "0x". */
private fun hexOf(c: RgbaColor): String = "%08X".format(c.toArgb())

@Composable
private fun FibonacciTimeline(
    state: StudioState,
    onChanged: () -> Unit
) {
    val expanded = state.timelineExpanded
    val alpha by animateFloatAsState(if (expanded) 1f else 0f, label = "alpha")
    val scale by animateFloatAsState(if (expanded) 1f else 0.4f, label = "scale")
    
    // Ambient Drift (Organic Fibonacci motion)
    val driftX by animateFloatAsState(if (expanded) 0f else 4f, label = "driftX")
    val driftY by animateFloatAsState(if (expanded) 0f else 4f, label = "driftY")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (expanded) 400.dp else 60.dp)
            .padding(8.dp)
            .offset(x = driftX.dp, y = driftY.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        // The Trigger Button (Bottom Left)
        DonutIconButton(
            icon = Icons.Filled.MoreTime,
            contentDescription = "Timeline",
            active = expanded,
            onClick = { state.timelineExpanded = !state.timelineExpanded }
        )

        if (expanded || alpha > 0f) {
            Box(
                modifier = Modifier
                    .offset(y = (-50).dp) // Move above the trigger
                    .graphicsLayer(
                        alpha = alpha,
                        scaleX = scale,
                        scaleY = scale,
                        transformOrigin = TransformOrigin(0f, 1f)
                    )
            ) {
                // Fibonacci Spiral Layout (Simplified for UI utility)
                // Squares grow from the bottom-left pivot

                // 1. Playback (40dp)
                FibonacciSquare(40, 0, 0) {
                    IconButton(onClick = { state.togglePlay() }) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White)
                    }
                }

                // 2. FPS (40dp)
                FibonacciSquare(40, 44, 0) {
                    Text("${state.project.canvas.fps}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }

                // 3. Loop/Range & Symmetry (80dp)
                FibonacciSquare(80, 0, 44) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row {
                            IconButton(onClick = { state.setInPointToCurrent(); onChanged() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.FirstPage, null, tint = Color.White)
                            }
                            IconButton(onClick = { state.setOutPointToCurrent(); onChanged() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.LastPage, null, tint = Color.White)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { state.toggleLoop(); onChanged() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Repeat, null, tint = if (state.scene.loop) Color.Cyan else Color.White)
                            }
                            if (state.symmetryEnabled) {
                                Text(
                                    "${state.symmetryCount}",
                                    color = Color.Cyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickableNoRipple {
                                        state.symmetryCount = if (state.symmetryCount >= 12) 2 else state.symmetryCount + 2
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. Scene/Frame Ops (120dp)
                FibonacciSquare(120, 88, 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { state.insertFrame(); onChanged() }) {
                            Icon(Icons.Filled.AddBox, "Insert", tint = Color.White)
                        }
                        IconButton(onClick = { state.removeFrame(); onChanged() }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = Color.White)
                        }
                        Text("Scene", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // 5. Layer Management (200dp)
                FibonacciSquare(200, 0, 128) {
                    Column(Modifier.fillMaxSize().padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Layers", 
                                color = Color.White, 
                                fontWeight = FontWeight.ExtraBold,
                                style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 4f))
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { state.addLayer(); onChanged() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Add, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        state.scene.layers.asReversed().forEach { layer ->
                            val active = layer.id == state.activeLayerId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(if (active) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                                    .pointerInput(layer.id) {
                                        detectDragGestures(
                                            onDragStart = { 
                                                state.activeLayerId = layer.id
                                            },
                                            onDragEnd = {
                                                if (state.altPressed) {
                                                    state.duplicateLayer(layer.id)
                                                    state.statusMessage = "Specter Duplicate created"
                                                }
                                            },
                                            onDrag = { change, dragAmount -> 
                                                change.consume()
                                                // Fibonacci Reordering: Detect if dragged over another layer
                                                val yOffset = dragAmount.y
                                                if (Math.abs(yOffset) > 20f) {
                                                    val layers = state.scene.layers
                                                    val currentIdx = layers.indexOfFirst { it.id == layer.id }
                                                    val nextIdx = if (yOffset > 0) currentIdx - 1 else currentIdx + 1
                                                    if (nextIdx in layers.indices) {
                                                        state.swapLayers(layer.id, layers[nextIdx].id)
                                                        onChanged()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    .clickableNoRipple { state.activeLayerId = layer.id; onChanged() }
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.DragHandle, 
                                    null, 
                                    tint = if (active) Color.Cyan else Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    layer.name, 
                                    color = if (active) Color.White else Color.Gray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (active) {
                                    Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }

                // 6. Frame Strip (320dp width, 120dp height)
                FibonacciSquare(320, 128, 128, height = 200) {
                    Column(Modifier.fillMaxSize()) {
                        Text("Timeline", color = Color.White, modifier = Modifier.padding(4.dp))
                        Box(Modifier.weight(1f)) {
                            FrameStripForLayer(
                                state = state,
                                layer = state.activeLayer,
                                active = true,
                                onFrame = { state.setFrame(it); onChanged() },
                                onMoved = onChanged
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FibonacciSquare(
    size: Int,
    offsetX: Int,
    offsetY: Int,
    height: Int = size,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .offset(x = offsetX.dp, y = (-offsetY).dp)
            .size(width = size.dp, height = height.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                ComposeBrush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A20).copy(alpha = 0.85f),
                        Color(0xFF0A0A0C).copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.Cyan.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.medium
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** A comprehensive timeline showing playback controls plus all scene layers. */
@Composable
                            startX = dragStartX[0],
                            endX = dragLastX[0],
                            frameCount = frameCount,
                            cellWidth = cellWpx,
                            spacing = gapPx,
                        ) { state.hasCelAt(it) }
                        if (drag != null && drag.isMove) {
                            state.moveCel(drag.from, drag.to)
                            onMoved()
                        }
                        dragStartX[0] = -1f
                    },
                    onDrag = { change, _ -> dragLastX[0] = change.position.x },
                )
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val range = state.scene.playbackRange
        for (f in 0 until frameCount) {
            val active = f == state.currentFrame
            val hasCel = state.activeLayer.cels.containsKey(f)
            val inRange = f in range
            val isEdge = f == range.first || f == range.last
            Box(
                Modifier
                    .size(width = cellW, height = 28.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        when {
                            active -> MaterialTheme.colorScheme.primary
                            hasCel -> Color(0xFF4A4A52)
                            else -> Color(0xFF333339)
                        }.let { base -> if (inRange) base else base.copy(alpha = 0.4f) },
                    )
                    // Mark the loop in/out edges with a secondary accent underline bar.
                    .then(
                        if (isEdge) Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            shape = MaterialTheme.shapes.small,
                        ) else Modifier,
                    )
                    .clickableNoRipple { onFrame(f) },
            )
        }
    }
}

@Composable
private fun TimelineAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon,
            contentDescription = description,
            tint = tint ?: if (enabled) Color.White else Color(0xFF55555C),
        )
    }
}

/** Compact −/value/+ stepper for the project frame rate. */
@Composable
private fun FpsStepper(fps: Int, onFps: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onFps(fps - 1) }, enabled = fps > 1, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Remove, "Slower", tint = if (fps > 1) Color.White else Color(0xFF55555C))
        }
        Text("${fps}fps", color = Color.White)
        IconButton(onClick = { onFps(fps + 1) }, enabled = fps < 120, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Add, "Faster", tint = if (fps < 120) Color.White else Color(0xFF55555C))
        }
    }
}

@Composable
private fun LayerRow(
    layer: com.inkframe.core.model.Layer,
    active: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    deletable: Boolean,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = if (active) Color(0xFF3A3A44) else Color(0xFF2C2C32),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(2.dp)) {
            IconButton(onClick = onToggleVisible, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (layer.visible) "Hide layer" else "Show layer",
                    tint = if (layer.visible) Color.White else Color(0xFF777780),
                )
            }
            Text(
                layer.name,
                color = if (layer.visible) Color.White else Color(0xFF999AA2),
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clickableNoRipple(onSelect),
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, "Move up",
                    tint = if (canMoveUp) Color.White else Color(0xFF55555C))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, "Move down",
                    tint = if (canMoveDown) Color.White else Color(0xFF55555C))
            }
            IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, "Rename layer", tint = Color.White)
            }
            IconButton(onClick = onDelete, enabled = deletable, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "Delete layer",
                    tint = if (deletable) Color.White else Color(0xFF55555C))
            }
        }
    }
}

@Composable
private fun RenameLayerDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename layer") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Layer name") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlendModePicker(
    current: BlendMode,
    onSelect: (BlendMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Blend", color = Color.White, modifier = Modifier.weight(1f))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            // A compact, tappable field showing the current mode.
            Surface(
                color = Color(0xFF2C2C32),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .menuAnchor()
                    .widthIn(min = 110.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(current.displayName, color = Color.White, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White)
                }
            }
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                BlendMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.displayName) },
                        onClick = { onSelect(mode); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun SidePanel(state: StudioState, onChanged: () -> Unit) {
    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(
                ComposeBrush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent, 
                        Color(0xFF1A1A20).copy(alpha = 0.4f),
                        Color.Black.copy(alpha = 0.6f)
                    )
                )
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Layers", color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { state.addLayer(); onChanged() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add layer", tint = Color.White)
            }
        }
        // Top of the stack appears first in the panel (reversed list order).
        val layerCount = state.scene.layers.size
        state.scene.layers.asReversed().forEachIndexed { revIndex, layer ->
            val stackIndex = layerCount - 1 - revIndex
            LayerRow(
                layer = layer,
                active = layer.id == state.activeLayerId,
                canMoveUp = stackIndex < layerCount - 1,
                canMoveDown = stackIndex > 0,
                onSelect = { state.activeLayerId = layer.id; onChanged() },
                onToggleVisible = { state.toggleLayerVisible(layer.id); onChanged() },
                onMoveUp = { state.moveLayerUp(layer.id); onChanged() },
                onMoveDown = { state.moveLayerDown(layer.id); onChanged() },
                onRename = { state.renamingLayerId = layer.id },
                onDelete = { state.deleteLayer(layer.id); onChanged() },
                deletable = layerCount > 1,
            )
        }

        // Opacity + blend mode for the active layer.
        val active = state.activeLayer
        LabeledSlider(
            label = "Layer opacity", value = active.opacity, range = 0f..1f,
            valueText = percent(active.opacity),
        ) { v -> state.setLayerOpacity(active.id, v); onChanged() }
        BlendModePicker(
            current = active.blendMode,
            onSelect = { mode -> state.setLayerBlendMode(active.id, mode); onChanged() },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Color", color = Color.White, modifier = Modifier.weight(1f))
            // Current colour swatch — tap to open the HSV picker.
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(state.color.toArgb()))
                    .border(2.dp, Color.White, CircleShape)
                    .clickableNoRipple { state.showColorPicker = true },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            state.project.colorPalette.forEach { c ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(c.toArgb()))
                        .clickableNoRipple { state.commitColor(c); onChanged() },
                )
            }
        }
        if (!state.recentColors.isEmpty()) {
            Text("Recent", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.recentColors.colors.take(6).forEach { c ->
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(c.toArgb()))
                            .clickableNoRipple { state.commitColor(c); onChanged() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onMp4: () -> Unit,
    onGif: () -> Unit,
    onPngSequence: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export animation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Exports use the scene's playback range at the project frame rate.")
                TextButton(onClick = onMp4, modifier = Modifier.fillMaxWidth()) { Text("Video (.mp4)") }
                TextButton(onClick = onGif, modifier = Modifier.fillMaxWidth()) { Text("Animated GIF") }
                TextButton(onClick = onPngSequence, modifier = Modifier.fillMaxWidth()) { Text("PNG sequence (.zip)") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Simple tap handler used for palette swatches and layer rows. */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    )

@Composable
private fun FrameStripForLayer(
    state: StudioState,
    layer: com.inkframe.core.model.Layer,
    active: Boolean,
    onFrame: (Int) -> Unit,
    onMoved: () -> Unit,
) {
    val cellW = 14.dp
    val frameCount = state.scene.frameCount
    val range = state.scene.playbackRange

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (f in 0 until frameCount) {
            val isCurrent = f == state.currentFrame
            val hasCel = layer.cels.containsKey(f)
            val inRange = f in range
            
            Box(
                Modifier
                    .size(width = cellW, height = 20.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        when {
                            isCurrent && active -> MaterialTheme.colorScheme.primary
                            isCurrent -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                            hasCel -> Color(0xFF4A4A52)
                            else -> Color(0xFF333339)
                        }.let { base -> if (inRange) base else base.copy(alpha = 0.2f) }
                    )
                    .clickableNoRipple { 
                        if (active) onFrame(f) 
                        else {
                            state.activeLayerId = layer.id
                            onFrame(f)
                        }
                    }
            )
        }
    }
}

private fun percent(v: Float) = "${(v * 100).toInt()}%"
