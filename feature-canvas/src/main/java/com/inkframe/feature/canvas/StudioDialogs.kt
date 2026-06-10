package com.inkframe.feature.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.inkframe.core.model.Brush
import com.inkframe.core.model.DefaultBrushes
import com.inkframe.core.model.MediaTypes

@Composable
fun BrushLibraryDialog(
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
fun RenameLayerDialog(
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

@Composable
fun ExportDialog(
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

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
