package eu.kanade.tachiyomi.data.translation.engine

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import eu.kanade.domain.translation.service.TranslationPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

object TextTranslator {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun translateTexts(
        texts: List<String>,
        fromLang: String,
        toLang: String,
        preferences: TranslationPreferences,
    ): List<String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()

        when (preferences.translatorEngine.get()) {
            TranslationPreferences.TranslatorEngine.ML_KIT -> translateWithMlKit(texts, fromLang, toLang)
            TranslationPreferences.TranslatorEngine.GOOGLE_WEB -> translateWithGoogleWeb(texts, fromLang, toLang)
            TranslationPreferences.TranslatorEngine.GEMINI -> translateWithGemini(
                texts,
                fromLang,
                toLang,
                preferences.geminiApiKey.get(),
                preferences.geminiModel.get(),
            )
            TranslationPreferences.TranslatorEngine.OPEN_ROUTER -> translateWithOpenRouter(
                texts,
                fromLang,
                toLang,
                preferences.openRouterApiKey.get(),
                preferences.openRouterModel.get(),
            )
        }
    }

    private suspend fun translateWithMlKit(
        texts: List<String>,
        fromLang: String,
        toLang: String,
    ): List<String> {
        val sourceMlKitLang = mapToMlKitLang(fromLang)
        val targetMlKitLang = mapToMlKitLang(toLang)

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceMlKitLang)
            .setTargetLanguage(targetMlKitLang)
            .build()

        val translator = Translation.getClient(options)
        return try {
            translator.downloadModelIfNeeded().await()
            texts.map { text ->
                if (text.isBlank()) "" else translator.translate(text).await()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "ML Kit Translation failed, falling back to Google Web" }
            translateWithGoogleWeb(texts, fromLang, toLang)
        } finally {
            try {
                translator.close()
            } catch (_: Throwable) {}
        }
    }

    private fun mapToMlKitLang(lang: String): String {
        return when (lang.lowercase()) {
            "ja", "japanese" -> TranslateLanguage.JAPANESE
            "zh", "chinese" -> TranslateLanguage.CHINESE
            "id", "indonesian" -> TranslateLanguage.INDONESIAN
            "es", "spanish" -> TranslateLanguage.SPANISH
            "fr", "french" -> TranslateLanguage.FRENCH
            "de", "german" -> TranslateLanguage.GERMAN
            "ru", "russian" -> TranslateLanguage.RUSSIAN
            "ko", "korean" -> TranslateLanguage.KOREAN
            else -> TranslateLanguage.ENGLISH
        }
    }

    private fun translateWithGoogleWeb(
        texts: List<String>,
        fromLang: String,
        toLang: String,
    ): List<String> {
        val client = Injekt.get<NetworkHelper>().client
        return texts.map { text ->
            if (text.isBlank()) return@map ""
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$fromLang&tl=$toLang&dt=t&q=$encodedText"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body.string()
                    val jsonArray = json.parseToJsonElement(bodyString).jsonArray
                    val sentences = jsonArray[0].jsonArray
                    val translated = StringBuilder()
                    for (sentence in sentences) {
                        translated.append(sentence.jsonArray[0].jsonPrimitive.content)
                    }
                    translated.toString()
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Google Web Translate error for: $text" }
                text
            }
        }
    }

    private fun translateWithGemini(
        texts: List<String>,
        fromLang: String,
        toLang: String,
        apiKey: String,
        modelName: String,
    ): List<String> {
        if (apiKey.isBlank()) {
            return translateWithGoogleWeb(texts, fromLang, toLang)
        }
        val client = Injekt.get<NetworkHelper>().client
        val model = if (modelName.isBlank()) "gemini-1.5-flash" else modelName
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val promptText = "Translate the following comic speech bubbles from $fromLang to $toLang. Provide translations as JSON array of strings in exact order:\n" +
            json.encodeToString(texts)

        val bodyJson = buildJsonObject {
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        putJsonArray("parts") {
                            add(
                                buildJsonObject {
                                    put("text", promptText)
                                },
                            )
                        }
                    },
                )
            }
        }

        return try {
            val requestBody = bodyJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()
            client.newCall(request).execute().use { response ->
                val bodyString = response.body.string()
                val responseJson = json.parseToJsonElement(bodyString).jsonObject
                val candidates = responseJson["candidates"]?.jsonArray
                val content = candidates?.get(0)?.jsonObject?.get("content")?.jsonObject
                val parts = content?.get("parts")?.jsonArray
                val responseText = parts?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()

                val jsonStart = responseText.indexOf('[')
                val jsonEnd = responseText.lastIndexOf(']')
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    val jsonArrayStr = responseText.substring(jsonStart, jsonEnd + 1)
                    val array = json.parseToJsonElement(jsonArrayStr).jsonArray
                    array.map { it.jsonPrimitive.content }
                } else {
                    translateWithGoogleWeb(texts, fromLang, toLang)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Gemini translation failed" }
            translateWithGoogleWeb(texts, fromLang, toLang)
        }
    }

    private fun translateWithOpenRouter(
        texts: List<String>,
        fromLang: String,
        toLang: String,
        apiKey: String,
        modelName: String,
    ): List<String> {
        if (apiKey.isBlank()) {
            return translateWithGoogleWeb(texts, fromLang, toLang)
        }
        val client = Injekt.get<NetworkHelper>().client
        val model = if (modelName.isBlank()) "google/gemini-flash-1.5" else modelName
        val url = "https://openrouter.ai/api/v1/chat/completions"

        val promptText = "Translate the following comic speech bubbles from $fromLang to $toLang. Return ONLY a JSON array of translated strings corresponding to the input list:\n" +
            json.encodeToString(texts)

        val bodyJson = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", promptText)
                    },
                )
            }
        }

        return try {
            val requestBody = bodyJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body.string()
                val responseJson = json.parseToJsonElement(bodyString).jsonObject
                val choices = responseJson["choices"]?.jsonArray
                val message = choices?.get(0)?.jsonObject?.get("message")?.jsonObject
                val responseText = message?.get("content")?.jsonPrimitive?.content.orEmpty()

                val jsonStart = responseText.indexOf('[')
                val jsonEnd = responseText.lastIndexOf(']')
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    val jsonArrayStr = responseText.substring(jsonStart, jsonEnd + 1)
                    val array = json.parseToJsonElement(jsonArrayStr).jsonArray
                    array.map { it.jsonPrimitive.content }
                } else {
                    translateWithGoogleWeb(texts, fromLang, toLang)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OpenRouter translation failed" }
            translateWithGoogleWeb(texts, fromLang, toLang)
        }
    }
}
