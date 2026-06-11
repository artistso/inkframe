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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkframe.core.model.*
import androidx.compose.ui.graphics.Brush as ComposeBrush

@Composable
fun SidePanel(state: StudioState, onChanged: () -> Unit) {
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

        val active = state.activeLayer
        LabeledSlider(
            label = "Layer opacity", value = active.opacity, range = 0f..1f,
            valueText = inkFramePercent(active.opacity),
        ) { v -> state.setLayerOpacity(active.id, v); onChanged() }
        
        // Color Palette
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Color", color = Color.White, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(state.color.toArgb()))
                    .border(2.dp, Color.White, CircleShape)
                    .clickableNoRipple { state.showColorPicker = true },
            )
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(valueText, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = value, valueRange = range, onValueChange = onValueChange)
    }
}

@Composable
fun LayerRow(
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
        color = if (active) Color(0xFF3A3A44) else Color(0xFF2C2C32).copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(2.dp)) {
            IconButton(onClick = onToggleVisible, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = null,
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
        }
    }
}

fun inkFramePercent(v: Float): String = "${(v * 100f).toInt()}%"

// ─── Brush Rail ─────────────────────────────────────────────────────────────

@Composable
fun BrushRail(state: StudioState, onSelect: (Brush) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        // Library shortcut
        IconButton(
            onClick = { state.showBrushLibrary = true },
            modifier = Modifier.size(44.dp)
        ) {
            Icon(Icons.Filled.Add, "Brush Library", tint = Color(0xFF666677), modifier = Modifier.size(20.dp))
        }

        DefaultBrushes.all.forEach { brush ->
            val isSelected = brush.id == state.brush.id
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.18f)
                        else Color.Black.copy(alpha = 0.3f)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 0.dp,
                        color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickableNoRipple { onSelect(brush) }
            ) {
                Text(
                    text = brush.name.take(2).uppercase(),
                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFAAAAAA),
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// ─── Modifier Rail ──────────────────────────────────────────────────────────

@Composable
fun ModifierRail(state: StudioState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        RailIconButton(Icons.Filled.Edit, "Sculpt Mode", state.sculptMode) {
            state.sculptMode = !state.sculptMode
        }
        RailIconButton(Icons.Filled.Flip, "Symmetry", state.symmetryEnabled) {
            state.symmetryEnabled = !state.symmetryEnabled
        }
        RailIconButton(Icons.Filled.GridOn, "Perspective", state.perspectiveEnabled) {
            state.perspectiveEnabled = !state.perspectiveEnabled
        }
    }
}

@Composable
private fun RailIconButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Color(0xFF00E5FF) else Color(0xFF555566),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─── Top Toolbar ─────────────────────────────────────────────────────────────

@Composable
fun TopToolbar(
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
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // History
        IconButton(onClick = onUndo, enabled = state.canUndo) {
            Icon(
                Icons.AutoMirrored.Filled.Undo, "Undo",
                tint = if (state.canUndo) Color.White else Color(0xFF333344),
            )
        }
        IconButton(onClick = onRedo, enabled = state.canRedo) {
            Icon(
                Icons.AutoMirrored.Filled.Redo, "Redo",
                tint = if (state.canRedo) Color.White else Color(0xFF333344),
            )
        }

        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp), color = Color.White.copy(alpha = 0.12f))

        // File ops
        IconButton(onClick = onSave) { Icon(Icons.Filled.Save, "Save", tint = Color(0xFF888899)) }
        IconButton(onClick = onOpen) { Icon(Icons.Filled.FolderOpen, "Open", tint = Color(0xFF888899)) }
        IconButton(onClick = onExport) { Icon(Icons.Filled.FileDownload, "Export", tint = Color(0xFF888899)) }

        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp), color = Color.White.copy(alpha = 0.12f))

        // Viewport
        IconButton(onClick = onFit) { Icon(Icons.Filled.ZoomOutMap, "Fit", tint = Color(0xFF888899)) }
        Text("${state.zoomPercent}%", color = Color(0xFF666677), fontSize = 10.sp)

        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp), color = Color.White.copy(alpha = 0.12f))

        // Tool toggles
        ToolToggle(Icons.Filled.Colorize, "Eyedropper", state.eyedropperActive, onToggleEyedropper)
        ToolToggle(Icons.Filled.FormatColorFill, "Fill", state.fillActive, onToggleFill)
        ToolToggle(Icons.Filled.Layers, "Onion Skin", state.onionSkin.enabled, onToggleOnion)
        IconButton(onClick = onOpenOnionSettings, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Tune, "Onion Settings", tint = Color(0xFF555566), modifier = Modifier.size(14.dp))
        }
        ToolToggle(Icons.Filled.GridOn, "Checker", state.showChecker, onToggleChecker)

        Spacer(Modifier.weight(1f))

        state.statusMessage?.let { msg ->
            Text(msg, color = Color(0xFFFFD740), fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp))
        }
        if (state.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp).padding(end = 8.dp),
                color = Color(0xFF00E5FF),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun ToolToggle(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Color(0xFF00E5FF) else Color(0xFF555566),
        )
    }
}

// ─── VFX HUD ─────────────────────────────────────────────────────────────────

@Composable
fun VfxHud(state: StudioState) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.65f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        HudLine("Frame", "${state.currentFrame + 1} / ${state.scene.frameCount}")
        HudLine("Zoom", "${state.zoomPercent}%")
        HudLine("Layer", state.activeLayer.name)
        HudLine("Brush", state.brush.name)
        state.statusMessage?.let { HudLine("Status", it, Color(0xFFFFD740)) }
    }
}

@Composable
private fun HudLine(label: String, value: String, valueColor: Color = Color(0xFF00E5FF)) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color(0xFF666677), fontSize = 10.sp, modifier = Modifier.width(44.dp))
        Text(value, color = valueColor, fontSize = 10.sp)
    }
}
