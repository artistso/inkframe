package com.inkframe.core.model

/**
 * Parameters describing a brush. The GL engine consumes these to stamp textured
 * dabs along an input stroke. Pressure/tilt from the stylus modulate size & opacity.
 */
data class Brush(
    val id: String,
    val name: String,
    val kind: BrushKind = BrushKind.ROUND,
    /** Base diameter in canvas pixels. */
    val sizePx: Float = 24f,
    val minSizePx: Float = 2f,
    val opacity: Float = 1f,
    val flow: Float = 1f,
    val hardness: Float = 0.8f,
    /** Dab spacing as a fraction of the diameter (lower = smoother but heavier). */
    val spacing: Float = 0.08f,
    val pressureToSize: Boolean = true,
    val pressureToOpacity: Boolean = false,
    val smoothing: Float = 0.35f,
    /** 0.0 (raw) to 1.0 (heavy stabilization). */
    val stabilization: Float = 0f,
    /** Post-draw simplification level (0.0 to 1.0). CSP style. */
    val postCorrection: Float = 0f,
    /** Snap start/end to existing lines (0.0 to 1.0 threshold). */
    val vectorMagnet: Float = 0f,
    /** Whether this brush leaves a glowing vector trail animation. */
    val glowTrail: Boolean = false,
    /** Whether to snap rough sketches to perfect geometric shapes. */
    val smartShaping: Boolean = false,
    /** How points in a stroke are connected (Continuous dabs vs Point-to-Point lines). */
    val connection: BrushConnection = BrushConnection.DABS,
    /**
     * When true, repeated dabs within a single stroke accumulate (darken) — the
     * airbrush model. When false, the stroke is flattened to uniform coverage in a
     * scratch buffer first (no overlap darkening) and applied once at [opacity].
     */
    val buildUp: Boolean = false,
) {
    /** Effective diameter for a given normalized pressure (0..1). */
    fun diameterForPressure(pressure: Float): Float {
        if (!pressureToSize) return sizePx
        val p = pressure.coerceIn(0f, 1f)
        return minSizePx + (sizePx - minSizePx) * p
    }

    /**
     * Per-dab coverage written into the stroke scratch buffer. This intentionally does
     * NOT include the brush's overall [opacity] — that is applied a single time when the
     * finished stroke is composited onto the cel, which is what prevents overlapping
     * dabs from darkening. Pressure may still modulate per-dab flow when enabled.
     */
    fun flowForPressure(pressure: Float): Float {
        val base = flow
        return if (pressureToOpacity) base * pressure.coerceIn(0f, 1f) else base
    }
}

enum class BrushKind { ROUND, PENCIL, INK, AIRBRUSH, ERASER, MARKER }

enum class BrushConnection {
    /** Standard dabs stamped at intervals. */
    DABS,
    /** Points connected by solid lines (vector-like "Specter" connection). */
    LINES
}

object DefaultBrushes {
    val pencil = Brush("pencil", "Pencil", BrushKind.PENCIL, sizePx = 6f, hardness = 0.95f, spacing = 0.05f)
    val ink = Brush("ink", "Ink Pen", BrushKind.INK, sizePx = 14f, hardness = 0.9f, spacing = 0.04f)
    val round = Brush("round", "Round", BrushKind.ROUND, sizePx = 32f, hardness = 0.6f)
    val airbrush = Brush(
        "airbrush", "Airbrush", BrushKind.AIRBRUSH,
        sizePx = 64f, hardness = 0.15f, flow = 0.10f, spacing = 0.04f,
        pressureToOpacity = true, buildUp = true,
    )
    val marker = Brush("marker", "Marker", BrushKind.MARKER, sizePx = 40f, hardness = 0.7f, opacity = 0.85f)
    val eraser = Brush("eraser", "Eraser", BrushKind.ERASER, sizePx = 40f, hardness = 0.8f)

    val neonGlow = Brush(
        "neon_glow", "Neon Glow", BrushKind.INK,
        sizePx = 12f, hardness = 0.5f, glowTrail = true,
        smoothing = 0.6f, stabilization = 0.4f
    )

    val all = listOf(pencil, ink, round, airbrush, marker, eraser, neonGlow)
}
