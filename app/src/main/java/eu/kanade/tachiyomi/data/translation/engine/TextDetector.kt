package eu.kanade.tachiyomi.data.translation.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

data class TextRegion(
    val rect: RectF,
    val text: String = "",
)

object TextDetector {

    fun detectTextRegions(bitmap: Bitmap): List<RectF> {
        val width = bitmap.width
        val height = bitmap.height
        val regions = mutableListOf<RectF>()

        // Sample grid for speech bubble and text block candidate regions
        val stepX = max(1, width / 40)
        val stepY = max(1, height / 60)

        val thresholdMap = Array(width / stepX) { BooleanArray(height / stepY) }

        for (x in 0 until (width / stepX)) {
            for (y in 0 until (height / stepY)) {
                val px = x * stepX
                val py = y * stepY
                if (px < width && py < height) {
                    val pixel = bitmap.getPixel(px, py)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    // Text pixel heuristic (dark contrast or high variance)
                    val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    if (luminance < 110) {
                        thresholdMap[x][y] = true
                    }
                }
            }
        }

        // Merge adjacent detected points into bounding boxes
        val visited = Array(thresholdMap.size) { BooleanArray(thresholdMap[0].size) }
        val cols = thresholdMap.size
        val rows = thresholdMap[0].size

        for (x in 0 until cols) {
            for (y in 0 until rows) {
                if (thresholdMap[x][y] && !visited[x][y]) {
                    var minX = x
                    var maxX = x
                    var minY = y
                    var maxY = y

                    val queue = ArrayDeque<Pair<Int, Int>>()
                    queue.add(x to y)
                    visited[x][y] = true

                    while (queue.isNotEmpty()) {
                        val (cx, cy) = queue.removeFirst()
                        minX = min(minX, cx)
                        maxX = max(maxX, cx)
                        minY = min(minY, cy)
                        maxY = max(maxY, cy)

                        // Check neighbors within distance 2 for clustering text lines
                        for (dx in -2..2) {
                            for (dy in -2..2) {
                                val nx = cx + dx
                                val ny = cy + dy
                                if (nx in 0 until cols && ny in 0 until rows) {
                                    if (thresholdMap[nx][ny] && !visited[nx][ny]) {
                                        visited[nx][ny] = true
                                        queue.add(nx to ny)
                                    }
                                }
                            }
                        }
                    }

                    val boxW = (maxX - minX + 1) * stepX
                    val boxH = (maxY - minY + 1) * stepY
                    if (boxW >= stepX * 2 && boxH >= stepY * 2 && boxW < width * 0.9f && boxH < height * 0.9f) {
                        val rect = RectF(
                            (minX * stepX).toFloat(),
                            (minY * stepY).toFloat(),
                            ((maxX + 1) * stepX).toFloat(),
                            ((maxY + 1) * stepY).toFloat(),
                        )
                        regions.add(rect)
                    }
                }
            }
        }

        return mergeOverlappingRects(regions)
    }

    private fun mergeOverlappingRects(rects: List<RectF>): List<RectF> {
        if (rects.isEmpty()) return emptyList()
        val result = mutableListOf<RectF>()
        val sorted = rects.sortedBy { it.top }

        for (rect in sorted) {
            var merged = false
            for (i in result.indices) {
                val existing = result[i]
                if (RectF.intersects(existing, rect) || isClose(existing, rect)) {
                    existing.union(rect)
                    merged = true
                    break
                }
            }
            if (!merged) {
                result.add(RectF(rect))
            }
        }
        return result
    }

    private fun isClose(r1: RectF, r2: RectF, margin: Float = 20f): Boolean {
        val expanded = RectF(r1.left - margin, r1.top - margin, r1.right + margin, r1.bottom + margin)
        return RectF.intersects(expanded, r2)
    }
}
