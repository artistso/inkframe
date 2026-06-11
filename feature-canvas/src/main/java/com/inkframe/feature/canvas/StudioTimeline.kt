package com.inkframe.feature.canvas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkframe.core.model.*
import androidx.compose.ui.graphics.Brush as ComposeBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures

@Composable
fun FibonacciTimeline(
    state: StudioState,
    onChanged: () -> Unit
) {
    val expanded = state.timelineExpanded
    val alpha by animateFloatAsState(if (expanded) 1f else 0f, label = "alpha")
    val scale by animateFloatAsState(if (expanded) 1f else 0.4f, label = "scale")
    
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
        DonutIconButton(
            icon = Icons.Filled.MoreTime,
            contentDescription = "Timeline",
            active = expanded,
            onClick = { state.timelineExpanded = !state.timelineExpanded }
        )

        if (expanded || alpha > 0f) {
            Box(
                modifier = Modifier
                    .offset(y = (-50).dp)
                    .graphicsLayer(
                        alpha = alpha,
                        scaleX = scale,
                        scaleY = scale,
                        transformOrigin = TransformOrigin(0f, 1f)
                    )
            ) {
                FibonacciSquare(80, 0, 44) {
                    IconButton(onClick = { state.togglePlay() }) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White)
                    }
                }
                
                FibonacciSquare(200, 0, 128) {
                    LayerSquareContent(state, onChanged)
                }

                FibonacciSquare(320, 128, 128, height = 200) {
                    FrameSquareContent(state, onChanged)
                }
            }
        }
    }
}

@Composable
fun FibonacciSquare(
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
                    colors = listOf(Color(0xFF1A1A20).copy(alpha = 0.85f), Color(0xFF0A0A0C).copy(alpha = 0.95f))
                )
            )
            .border(1.dp, Color.Cyan.copy(alpha = 0.1f), MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun LayerSquareContent(state: StudioState, onChanged: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("Layers", color = Color.White, fontWeight = FontWeight.ExtraBold)
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
                            onDragStart = { state.activeLayerId = layer.id },
                            onDragEnd = {
                                if (state.altPressed) state.duplicateLayer(layer.id)
                            },
                            onDrag = { change, dragAmount -> 
                                change.consume()
                                if (Math.abs(dragAmount.y) > 20f) {
                                    val layers = state.scene.layers
                                    val currentIdx = layers.indexOfFirst { it.id == layer.id }
                                    val nextIdx = if (dragAmount.y > 0) currentIdx - 1 else currentIdx + 1
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
                Icon(Icons.Filled.DragHandle, null, tint = if (active) Color.Cyan else Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(layer.name, color = if (active) Color.White else Color.Gray, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FrameSquareContent(state: StudioState, onChanged: () -> Unit) {
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

// ─── Donut Icon Button ────────────────────────────────────────────────────────

@Composable
fun DonutIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (active) Color(0xFF00E5FF).copy(alpha = 0.15f)
                else Color.Black.copy(alpha = 0.55f)
            )
            .border(
                width = 1.5.dp,
                color = if (active) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.18f),
                shape = CircleShape,
            )
            .clickableNoRipple(onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(22.dp),
        )
    }
}

// ─── Frame Strip ──────────────────────────────────────────────────────────────

@Composable
private fun FrameStripForLayer(
    state: StudioState,
    layer: Layer,
    active: Boolean,
    onFrame: (Int) -> Unit,
    onMoved: () -> Unit,
) {
    val frameCount = state.scene.frameCount
    val currentFrame = state.currentFrame
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
    ) {
        for (i in 0 until frameCount) {
            val hasCel = layer.cels.containsKey(i)
            val isCurrent = i == currentFrame
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 24.dp, height = 40.dp)
                    .background(
                        when {
                            isCurrent -> Color(0xFF00E5FF).copy(alpha = 0.22f)
                            hasCel    -> Color(0xFF3A3A44)
                            else      -> Color(0xFF1A1A20)
                        }
                    )
                    .border(
                        width = 0.5.dp,
                        color = if (isCurrent) Color(0xFF00E5FF)
                                else Color.White.copy(alpha = 0.07f),
                    )
                    .pointerInput(i) {
                        detectDragGestures(
                            onDragStart = { onFrame(i) },
                            onDrag = { change, delta ->
                                change.consume()
                                val frameDelta = (delta.x / 24f).toInt()
                                if (frameDelta != 0 && state.hasCelAt(i)) {
                                    state.moveCel(i, (i + frameDelta).coerceIn(0, frameCount - 1))
                                    onMoved()
                                }
                            },
                            onDragEnd = {}
                        )
                    }
                    .clickableNoRipple { onFrame(i) }
            ) {
                if (hasCel) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCurrent) Color(0xFF00E5FF)
                                else Color(0xFF00E5FF).copy(alpha = 0.55f)
                            )
                    )
                }
            }
        }

        // Add-frame button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 24.dp, height = 40.dp)
                .background(Color(0xFF111114))
                .clickableNoRipple { state.insertFrame(); onMoved() }
        ) {
            Icon(
                Icons.Filled.Add, "Add Frame",
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
