package eu.kanade.tachiyomi.data.translation.engine

import android.content.Context
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream

enum class ModelType(val fileName: String, val url: String, val displayName: String) {
    DETECTION(
        "detector-v4-s_int8.onnx",
        "https://huggingface.co/ogkalu/comic-text-and-bubble-detector/resolve/main/detector-v4-s_int8.onnx",
        "Comic Text & Bubble Detector (ONNX)",
    ),
    OCR(
        "PP-OCRv6_small_rec.onnx",
        "https://huggingface.co/ogkalu/ppocr-v6-onnx/resolve/main/PP-OCRv6_small_rec.onnx",
        "PaddleOCR v6 Small (ONNX)",
    ),
    INPAINTING(
        "aot.onnx",
        "https://huggingface.co/ogkalu/aot-inpainting/resolve/main/aot.onnx",
        "Aot Inpainting (ONNX)",
    ),
}

object ModelDownloader {

    fun getModelFile(context: Context, modelType: ModelType): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return File(modelsDir, modelType.fileName)
    }

    fun isModelDownloaded(context: Context, modelType: ModelType): Boolean {
        val file = getModelFile(context, modelType)
        return file.exists() && file.length() > 1024L
    }

    suspend fun downloadModel(
        context: Context,
        modelType: ModelType,
        onProgress: (Float) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val destinationFile = getModelFile(context, modelType)
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")

        try {
            val client = Injekt.get<NetworkHelper>().client
            val request = Request.Builder().url(modelType.url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logcat(LogPriority.ERROR) { "Failed to download model ${modelType.displayName}: HTTP ${response.code}" }
                    return@withContext false
                }

                val body = response.body
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(8192)
                var downloadedBytes = 0L
                var read: Int

                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    downloadedBytes += read
                    if (contentLength > 0) {
                        onProgress(downloadedBytes.toFloat() / contentLength)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                if (tempFile.exists()) {
                    if (destinationFile.exists()) {
                        destinationFile.delete()
                    }
                    tempFile.renameTo(destinationFile)
                    logcat(LogPriority.INFO) { "Successfully downloaded model ${modelType.displayName}" }
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error downloading model ${modelType.displayName}" }
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        return@withContext false
    }

    fun deleteModel(context: Context, modelType: ModelType): Boolean {
        val file = getModelFile(context, modelType)
        return if (file.exists()) file.delete() else false
    }
}
