package com.inkframe.feature.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
