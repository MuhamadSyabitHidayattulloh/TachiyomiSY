package eu.kanade.tachiyomi.data.translation.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

object OcrRecognizer {

    suspend fun recognizeText(
        bitmap: Bitmap,
        regions: List<RectF>,
        sourceLang: String = "ja",
        context: Context? = null,
    ): List<TextRegion> {
        val results = mutableListOf<TextRegion>()

        val recognizer = try {
            when (sourceLang.lowercase()) {
                "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "ML Kit Text Recognizer fallback to default" }
            try {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            } catch (t: Throwable) {
                null
            }
        }

        if (recognizer != null) {
            for (region in regions) {
                try {
                    val croppedWidth = (region.width()).toInt().coerceAtLeast(1).coerceAtMost(bitmap.width)
                    val croppedHeight = (region.height()).toInt().coerceAtLeast(1).coerceAtMost(bitmap.height)
                    val left = (region.left.toInt()).coerceIn(0, bitmap.width - croppedWidth)
                    val top = (region.top.toInt()).coerceIn(0, bitmap.height - croppedHeight)

                    val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, croppedWidth, croppedHeight)
                    val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
                    val visionText = recognizer.process(inputImage).await()
                    val recognizedText = visionText.text.trim()

                    if (recognizedText.isNotBlank()) {
                        results.add(TextRegion(region, recognizedText))
                    }
                    croppedBitmap.recycle()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed OCR on region $region" }
                }
            }
            try {
                recognizer.close()
            } catch (_: Throwable) {}
        }

        if (results.isEmpty() && recognizer != null) {
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = recognizer.process(inputImage).await()
                for (block in visionText.textBlocks) {
                    val box = block.boundingBox
                    val text = block.text.trim()
                    if (box != null && text.isNotBlank()) {
                        results.add(TextRegion(RectF(box), text))
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed full image OCR" }
            }
        }

        return results
    }
}
