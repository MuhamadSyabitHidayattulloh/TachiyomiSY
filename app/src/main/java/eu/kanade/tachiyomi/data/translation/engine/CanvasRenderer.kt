package eu.kanade.tachiyomi.data.translation.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max

object CanvasRenderer {

    fun renderTranslation(
        cleanedBitmap: Bitmap,
        translatedRegions: List<TextRegion>,
        fontName: String = "Anime Ace",
    ): Bitmap {
        val result = cleanedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val typeface = when (fontName) {
            "Anime Ace", "Wild Words", "CC Comicrazy" -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            else -> Typeface.DEFAULT_BOLD
        }

        val textPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            this.typeface = typeface
        }

        val strokePaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
            this.typeface = typeface
        }

        for (region in translatedRegions) {
            val text = region.text.trim()
            if (text.isBlank()) continue

            val rect = region.rect
            val targetWidth = max(10, rect.width().toInt())
            val targetHeight = max(10, rect.height().toInt())

            // Find optimal text size to fit in bounding box
            var textSize = (targetHeight * 0.35f).coerceIn(12f, 60f)
            textPaint.textSize = textSize
            strokePaint.textSize = textSize

            var layout = createStaticLayout(text, textPaint, targetWidth)
            while (layout.height > targetHeight && textSize > 10f) {
                textSize -= 1f
                textPaint.textSize = textSize
                strokePaint.textSize = textSize
                layout = createStaticLayout(text, textPaint, targetWidth)
            }

            canvas.save()
            val startX = rect.left + (targetWidth - layout.width) / 2f
            val startY = rect.top + (targetHeight - layout.height) / 2f
            canvas.translate(startX, startY)

            // Draw white outline/shadow
            val strokeLayout = createStaticLayout(text, strokePaint, targetWidth)
            strokeLayout.draw(canvas)

            // Draw main text
            layout.draw(canvas)
            canvas.restore()
        }

        return result
    }

    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, max(1, width))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
    }
}
