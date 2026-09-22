package eu.kanade.domain.translation.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {
    enum class TranslatorEngine {
        ML_KIT,
        GOOGLE_WEB,
        GEMINI,
        OPEN_ROUTER,
    }

    val readerFont: Preference<String> = preferenceStore.getString("pref_translation_reader_font", FONT_ANIME_ACE)
    val translateFrom: Preference<String> = preferenceStore.getString("pref_translation_from", "ja")
    val translateTo: Preference<String> = preferenceStore.getString("pref_translation_to", "id")
    val translatorEngine: Preference<TranslatorEngine> = preferenceStore.getEnum("pref_translation_engine", TranslatorEngine.ML_KIT)

    val geminiApiKey: Preference<String> = preferenceStore.getString("pref_translation_gemini_api_key", "")
    val geminiModel: Preference<String> = preferenceStore.getString("pref_translation_gemini_model", "gemini-1.5-flash")

    val openRouterApiKey: Preference<String> = preferenceStore.getString("pref_translation_openrouter_api_key", "")
    val openRouterModel: Preference<String> = preferenceStore.getString("pref_translation_openrouter_model", "google/gemini-flash-1.5")

    val autoTranslateOnDownload: Preference<Boolean> = preferenceStore.getBoolean("pref_translation_auto_on_download", false)

    companion object {
        const val FONT_ANIME_ACE = "Anime Ace"
        const val FONT_WILD_WORDS = "Wild Words"
        const val FONT_COMICRAZY = "CC Comicrazy"
        const val FONT_SYSTEM = "System Default"

        val FONTS = listOf(FONT_ANIME_ACE, FONT_WILD_WORDS, FONT_COMICRAZY, FONT_SYSTEM)

        val SOURCE_LANGUAGES = mapOf(
            "en" to "English",
            "zh" to "Chinese",
            "ja" to "Japanese",
        )

        val TARGET_LANGUAGES = mapOf(
            "id" to "Indonesian",
            "en" to "English",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "ru" to "Russian",
            "zh" to "Chinese",
            "ja" to "Japanese",
            "ko" to "Korean",
            "pt" to "Portuguese",
            "it" to "Italian",
            "th" to "Thai",
            "vi" to "Vietnamese",
        )
    }
}
