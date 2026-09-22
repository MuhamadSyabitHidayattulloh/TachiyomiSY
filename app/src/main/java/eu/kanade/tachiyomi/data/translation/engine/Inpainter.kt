package eu.kanade.tachiyomi.data.translation.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

object Inpainter {

    fun inpaint(original: Bitmap, textRegions: List<TextRegion>): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        for (region in textRegions) {
            val rect = region.rect
            val sampleBgColor = sampleBorderColor(original, rect)
            paint.color = sampleBgColor

            // Expand rect slightly to cover text edges completely
            val expandedRect = RectF(
                max(0f, rect.left - 4f),
                max(0f, rect.top - 4f),
                min(original.width.toFloat(), rect.right + 4f),
                min(original.height.toFloat(), rect.bottom + 4f),
            )

            // Fill text area with sampled background color (inpainting/cleaning speech bubble)
            canvas.drawRoundRect(expandedRect, 8f, 8f, paint)
        }

        return result
    }

    private fun sampleBorderColor(bitmap: Bitmap, rect: RectF): Int {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val right = rect.right.toInt().coerceIn(0, bitmap.width - 1)
        val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val bottom = rect.bottom.toInt().coerceIn(0, bitmap.height - 1)

        // Sample top and bottom borders
        for (x in left..right step max(1, (right - left) / 10)) {
            val p1 = bitmap.getPixel(x, top)
            val p2 = bitmap.getPixel(x, bottom)
            rSum += Color.red(p1) + Color.red(p2)
            gSum += Color.green(p1) + Color.green(p2)
            bSum += Color.blue(p1) + Color.blue(p2)
            count += 2
        }

        // Sample left and right borders
        for (y in top..bottom step max(1, (bottom - top) / 10)) {
            val p1 = bitmap.getPixel(left, y)
            val p2 = bitmap.getPixel(right, y)
            rSum += Color.red(p1) + Color.red(p2)
            gSum += Color.green(p1) + Color.green(p2)
            bSum += Color.blue(p1) + Color.blue(p2)
            count += 2
        }

        return if (count > 0) {
            val avgR = (rSum / count).toInt().coerceIn(0, 255)
            val avgG = (gSum / count).toInt().coerceIn(0, 255)
            val avgB = (bSum / count).toInt().coerceIn(0, 255)

            // If border is predominantly light, default to pure white for crisp comic speech bubbles
            val luminance = 0.299 * avgR + 0.587 * avgG + 0.114 * avgB
            if (luminance > 180) {
                Color.WHITE
            } else {
                Color.rgb(avgR, avgG, avgB)
            }
        } else {
            Color.WHITE
        }
    }
}
