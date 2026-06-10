package com.inkframe.core.common

import kotlin.math.*

/**
 * High-precision geometric shape detection for "Smart Shaping".
 * Analyzes a sequence of points to identify lines, circles, and polygons.
 */
object ShapeDetector {

    data class DetectedShape(
        val type: ShapeType,
        val points: List<Vec2>,
        val confidence: Float
    )

    enum class ShapeType { LINE, CIRCLE, RECTANGLE, TRIANGLE, NONE }

    fun detect(points: List<Vec2>): DetectedShape {
        if (points.size < 5) return DetectedShape(ShapeType.NONE, points, 0f)

        // 1. Check for Line (simplest and most common)
        val lineConfidence = checkLine(points)
        if (lineConfidence > 0.92f) {
            return DetectedShape(ShapeType.LINE, listOf(points.first(), points.last()), lineConfidence)
        }

        // 2. Check for Circle/Ellipse
        val (center, radius, circleConf) = checkCircle(points)
        if (circleConf > 0.85f) {
            // Generate a perfect circle path
            val perfectPoints = (0..32).map { i ->
                val angle = 2 * PI * i / 32.0
                Vec2(
                    (center.x + cos(angle) * radius).toFloat(),
                    (center.y + sin(angle) * radius).toFloat()
                )
            }
            return DetectedShape(ShapeType.CIRCLE, perfectPoints, circleConf)
        }

        return DetectedShape(ShapeType.NONE, points, 0f)
    }

    private fun checkLine(points: List<Vec2>): Float {
        val start = points.first()
        val end = points.last()
        val totalDist = start.distanceTo(end)
        if (totalDist < 10f) return 0f

        var maxDev = 0f
        for (p in points) {
            val dev = distPointToLine(p, start, end)
            maxDev = max(maxDev, dev)
        }
        
        return (1f - (maxDev / (totalDist * 0.2f))).coerceIn(0f, 1f)
    }

    private fun checkCircle(points: List<Vec2>): Triple<Vec2, Float, Float> {
        val avgX = points.map { it.x }.average().toFloat()
        val avgY = points.map { it.y }.average().toFloat()
        val center = Vec2(avgX, avgY)
        
        val radii = points.map { it.distanceTo(center) }
        val avgRadius = radii.average().toFloat()
        
        if (avgRadius < 5f) return Triple(center, 0f, 0f)

        var variance = 0f
        for (r in radii) {
            variance += abs(r - avgRadius)
        }
        val avgDev = variance / points.size
        val confidence = (1f - (avgDev / avgRadius)).coerceIn(0f, 1f)
        
        return Triple(center, avgRadius, confidence)
    }

    private fun distPointToLine(p: Vec2, a: Vec2, b: Vec2): Float {
        val num = abs((b.x - a.x) * (a.y - p.y) - (a.x - p.x) * (b.y - a.y))
        val den = hypot(b.x - a.x, b.y - a.y)
        return if (den == 0f) 0f else num / den
    }
}
