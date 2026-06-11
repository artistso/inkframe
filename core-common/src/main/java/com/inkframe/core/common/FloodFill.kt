package com.inkframe.core.common

import android.graphics.Bitmap

/**
 * Scanline fill algorithm for flood-filling regions.
 */
object FloodFill {
    fun fill(pixels: IntArray, width: Int, height: Int, x: Int, y: Int, targetColor: Int, tolerance: Int): IntRect? {
        if (x !in 0 until width || y !in 0 until height) return null
        val seedColor = pixels[y * width + x]
        if (seedColor == targetColor) return null

        val stack = java.util.ArrayDeque<Int>()
        stack.push(x)
        stack.push(y)

        var minX = x
        var maxX = x
        var minY = y
        var maxY = y

        while (stack.isNotEmpty()) {
            val currY = stack.pop()
            val currX = stack.pop()

            var left = currX
            while (left > 0 && colorMatch(pixels[currY * width + left - 1], seedColor, tolerance)) {
                left--
            }

            var right = currX
            while (right < width - 1 && colorMatch(pixels[currY * width + right + 1], seedColor, tolerance)) {
                right++
            }

            for (i in left..right) {
                pixels[currY * width + i] = targetColor
                minX = Math.min(minX, i)
                maxX = Math.max(maxX, i)
                minY = Math.min(minY, currY)
                maxY = Math.max(maxY, currY)

                if (currY > 0 && colorMatch(pixels[(currY - 1) * width + i], seedColor, tolerance)) {
                    if (i == left || !colorMatch(pixels[(currY - 1) * width + i - 1], seedColor, tolerance)) {
                        stack.push(i)
                        stack.push(currY - 1)
                    }
                }
                if (currY < height - 1 && colorMatch(pixels[(currY + 1) * width + i], seedColor, tolerance)) {
                    if (i == left || !colorMatch(pixels[(currY + 1) * width + i - 1], seedColor, tolerance)) {
                        stack.push(i)
                        stack.push(currY + 1)
                    }
                }
            }
        }
        return IntRect(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private fun colorMatch(c1: Int, c2: Int, tol: Int): Boolean {
        if (c1 == c2) return true
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        val a1 = (c1 shr 24) and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        val a2 = (c2 shr 24) and 0xFF
        return Math.abs(r1 - r2) <= tol && Math.abs(g1 - g2) <= tol &&
               Math.abs(b1 - b2) <= tol && Math.abs(a1 - a2) <= tol
    }
}
