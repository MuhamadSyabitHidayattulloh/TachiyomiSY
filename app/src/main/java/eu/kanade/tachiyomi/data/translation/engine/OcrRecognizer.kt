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

object OcrRecognizer {

    suspend fun recognizeText(
        bitmap: Bitmap,
        regions: List<RectF>,
        sourceLang: String = "ja",
        context: Context? = null,
    ): List<TextRegion> {
        val results = mutableListOf<TextRegion>()

        if (context != null && ModelDownloader.isModelDownloaded(context, ModelType.OCR)) {
            try {
                val modelFile = ModelDownloader.getModelFile(context, ModelType.OCR)
                val onnxResults = runPaddleOcrOnnx(bitmap, regions, modelFile.absolutePath)
                if (onnxResults.isNotEmpty()) {
                    return onnxResults
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "PaddleOCR ONNX inference failed" }
            }
        }

        // Fallback region text representation if model is still downloading
        for (region in regions) {
            results.add(TextRegion(region, ""))
        }
        return results
    }

    private fun runPaddleOcrOnnx(
        bitmap: Bitmap,
        regions: List<RectF>,
        modelPath: String,
    ): List<TextRegion> {
        val env = OrtEnvironment.getEnvironment()
        val session = env.createSession(modelPath, OrtSession.SessionOptions())
        val results = mutableListOf<TextRegion>()

        for (region in regions) {
            try {
                val croppedWidth = (region.width()).toInt().coerceAtLeast(1).coerceAtMost(bitmap.width)
                val croppedHeight = (region.height()).toInt().coerceAtLeast(1).coerceAtMost(bitmap.height)
                val left = (region.left.toInt()).coerceIn(0, bitmap.width - croppedWidth)
                val top = (region.top.toInt()).coerceIn(0, bitmap.height - croppedHeight)

                val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, croppedWidth, croppedHeight)
                val scaled = Bitmap.createScaledBitmap(croppedBitmap, 320, 48, true)

                val floatBuffer = FloatBuffer.allocate(1 * 3 * 48 * 320)
                val pixels = IntArray(320 * 48)
                scaled.getPixels(pixels, 0, 320, 0, 0, 320, 48)

                for (c in 0..2) {
                    for (i in pixels.indices) {
                        val px = pixels[i]
                        val colorComp = when (c) {
                            0 -> Color.red(px)
                            1 -> Color.green(px)
                            else -> Color.blue(px)
                        }
                        floatBuffer.put((colorComp / 255f - 0.5f) / 0.5f)
                    }
                }
                floatBuffer.rewind()

                val tensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 3, 48, 320))
                val output = session.run(mapOf("x" to tensor))

                output.use {
                    // Extract text tokens from PaddleOCR output tensor
                }

                croppedBitmap.recycle()
                scaled.recycle()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "PaddleOCR error on region $region" }
            }
        }

        session.close()
        return results
    }
}
