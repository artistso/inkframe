package com.inkframe.core.model

import com.inkframe.core.common.Vec2

/**
 * Stores the raw high-fidelity data of a single brush stroke.
 * This is the "vector" representation that allows for non-destructive effects,
 * like trailing animations, path editing, or re-rendering with different brushes.
 */
data class StrokeData(
    val brushId: String,
    val color: RgbaColor,
    val points: List<StrokePoint>,
    val timestamp: Long = System.currentTimeMillis()
)

data class StrokePoint(
    val pos: Vec2,
    val pressure: Float,
    val timeOffsetMs: Long
)

/**
 * A collection of strokes for a specific cel.
 * This can live alongside the bitmap representation.
 */
data class CelVectorData(
    val strokes: List<StrokeData> = emptyList()
)
