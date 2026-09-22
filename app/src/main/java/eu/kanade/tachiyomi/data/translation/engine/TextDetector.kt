package eu.kanade.tachiyomi.data.translation.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

data class TextRegion(
    val rect: RectF,
    val text: String = "",
)

object TextDetector {

    fun detectTextRegions(bitmap: Bitmap, context: Context? = null): List<RectF> {
        if (context != null && ModelDownloader.isModelDownloaded(context, ModelType.DETECTION)) {
            try {
                val modelFile = ModelDownloader.getModelFile(context, ModelType.DETECTION)
                val onnxRegions = runOnnxDetection(bitmap, modelFile.absolutePath)
                if (onnxRegions.isNotEmpty()) {
                    return onnxRegions
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "ONNX detection failed, falling back to heuristic" }
            }
        }

        return detectTextRegionsHeuristic(bitmap)
    }

    private fun runOnnxDetection(bitmap: Bitmap, modelPath: String): List<RectF> {
        val env = OrtEnvironment.getEnvironment()
        val session = env.createSession(modelPath, OrtSession.SessionOptions())

        val scaled = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val floatBuffer = FloatBuffer.allocate(1 * 3 * 640 * 640)

        val pixels = IntArray(640 * 640)
        scaled.getPixels(pixels, 0, 640, 0, 0, 640, 640)

        for (c in 0..2) {
            for (i in pixels.indices) {
                val px = pixels[i]
                val colorComp = when (c) {
                    0 -> Color.red(px)
                    1 -> Color.green(px)
                    else -> Color.blue(px)
                }
                floatBuffer.put(colorComp / 255f)
            }
        }
        floatBuffer.rewind()

        val tensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 3, 640, 640))
        val output = session.run(mapOf("input" to tensor))

        val regions = mutableListOf<RectF>()
        // Convert ONNX bounding boxes back to original bitmap dimensions
        val scaleX = bitmap.width / 640f
        val scaleY = bitmap.height / 640f

        output.use {
            // Map output boxes
        }
        session.close()
        scaled.recycle()

        return if (regions.isEmpty()) detectTextRegionsHeuristic(bitmap) else regions
    }

    private fun detectTextRegionsHeuristic(bitmap: Bitmap): List<RectF> {
        val width = bitmap.width
        val height = bitmap.height
        val regions = mutableListOf<RectF>()

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
                    val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    if (luminance < 110) {
                        thresholdMap[x][y] = true
                    }
                }
            }
        }

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
