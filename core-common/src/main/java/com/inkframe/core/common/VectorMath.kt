package com.inkframe.core.common

import kotlin.math.*

/**
 * Advanced vector path algorithms for "Correction" and "Magnetism".
 */
object VectorMath {

    /**
     * Ramer-Douglas-Peucker algorithm for path simplification.
     * Reduces the number of points in a path while preserving its shape.
     */
    fun simplify(points: List<Vec2>, epsilon: Float): List<Vec2> {
        if (points.size < 3) return points

        var maxDist = 0f
        var index = 0
        val end = points.size - 1

        for (i in 1 until end) {
            val dist = distPointToLine(points[i], points[0], points[end])
            if (dist > maxDist) {
                maxDist = dist
                index = i
            }
        }

        return if (maxDist > epsilon) {
            val left = simplify(points.subList(0, index + 1), epsilon)
            val right = simplify(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(points[0], points[end])
        }
    }

    private fun distPointToLine(p: Vec2, a: Vec2, b: Vec2): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val L2 = dx * dx + dy * dy
        if (L2 == 0f) return p.distanceTo(a)
        
        var t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / L2
        t = max(0f, min(1f, t))
        
        return p.distanceTo(Vec2(a.x + t * dx, a.y + t * dy))
    }

    /**
     * Finds the nearest point on a set of existing paths.
     * Used for "Vector Magnet" snapping.
     */
    fun findSnapPoint(
        point: Vec2, 
        existingStrokes: List<List<Vec2>>, 
        threshold: Float
    ): Vec2? {
        var bestPoint: Vec2? = null
        var bestDist = threshold

        for (stroke in existingStrokes) {
            for (i in 0 until stroke.size - 1) {
                val a = stroke[i]
                val b = stroke[i + 1]
                
                // Find nearest point on this segment
                val dx = b.x - a.x
                val dy = b.y - a.y
                val L2 = dx * dx + dy * dy
                if (L2 == 0f) continue
                
                var t = ((point.x - a.x) * dx + (point.y - a.y) * dy) / L2
                t = max(0f, min(1f, t))
                val pOnSeg = Vec2(a.x + t * dx, a.y + t * dy)
                
                val d = point.distanceTo(pOnSeg)
                if (d < bestDist) {
                    bestDist = d
                    bestPoint = pOnSeg
                }
            }
        }
        return bestPoint
    }
}
