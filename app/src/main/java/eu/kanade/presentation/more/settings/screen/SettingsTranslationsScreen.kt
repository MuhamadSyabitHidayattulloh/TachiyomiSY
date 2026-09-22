package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.domain.translation.service.TranslationPreferences
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTranslationsScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = SYMR.strings.pref_category_translations

    @Composable
    override fun getPreferences(): List<Preference> {
        val translationPreferences: TranslationPreferences = remember { Injekt.get() }

        return listOf(
            Preference.PreferenceGroup(
                stringResource(SYMR.strings.pref_category_translations),
                preferenceItems = listOf(
                    readerFont(translationPreferences),
                    translateFrom(translationPreferences),
                    translateTo(translationPreferences),
                    translatorEngine(translationPreferences),
                ),
            ),
            Preference.PreferenceGroup(
                "API & AI Models",
                preferenceItems = listOf(
                    geminiApiKey(translationPreferences),
                    geminiModel(translationPreferences),
                    openRouterApiKey(translationPreferences),
                    openRouterModel(translationPreferences),
                ),
            ),
            Preference.PreferenceGroup(
                stringResource(MR.strings.pref_category_downloads),
                preferenceItems = listOf(
                    autoTranslateOnDownload(translationPreferences),
                ),
            ),
        )
    }

    @Composable
    private fun readerFont(preferences: TranslationPreferences): Preference.PreferenceItem.ListPreference<String> {
        return Preference.PreferenceItem.ListPreference(
            preference = preferences.readerFont,
            title = stringResource(SYMR.strings.pref_translation_reader_font),
            subtitle = stringResource(SYMR.strings.pref_translation_reader_font_summary),
            entries = TranslationPreferences.FONTS.associateWith { it },
        )
    }

    @Composable
    private fun translateFrom(preferences: TranslationPreferences): Preference.PreferenceItem.ListPreference<String> {
        return Preference.PreferenceItem.ListPreference(
            preference = preferences.translateFrom,
            title = stringResource(SYMR.strings.pref_translation_from),
            entries = TranslationPreferences.SOURCE_LANGUAGES,
        )
    }

    @Composable
    private fun translateTo(preferences: TranslationPreferences): Preference.PreferenceItem.ListPreference<String> {
        return Preference.PreferenceItem.ListPreference(
            preference = preferences.translateTo,
            title = stringResource(SYMR.strings.pref_translation_to),
            entries = TranslationPreferences.TARGET_LANGUAGES,
        )
    }

    @Composable
    private fun translatorEngine(preferences: TranslationPreferences): Preference.PreferenceItem.ListPreference<TranslationPreferences.TranslatorEngine> {
        return Preference.PreferenceItem.ListPreference(
            preference = preferences.translatorEngine,
            title = stringResource(SYMR.strings.pref_translation_engine),
            entries = mapOf(
                TranslationPreferences.TranslatorEngine.ML_KIT to "ML-Kit Translate (Offline)",
                TranslationPreferences.TranslatorEngine.GOOGLE_WEB to "Google Translate (Web)",
                TranslationPreferences.TranslatorEngine.GEMINI to "Gemini AI",
                TranslationPreferences.TranslatorEngine.OPEN_ROUTER to "OpenRouter AI",
            ),
        )
    }

    @Composable
    private fun geminiApiKey(preferences: TranslationPreferences): Preference.PreferenceItem.EditTextPreference {
        return Preference.PreferenceItem.EditTextPreference(
            preference = preferences.geminiApiKey,
            title = stringResource(SYMR.strings.pref_translation_gemini_api_key),
            subtitle = "API key for Google Gemini",
        )
    }

    @Composable
    private fun geminiModel(preferences: TranslationPreferences): Preference.PreferenceItem.EditTextPreference {
        return Preference.PreferenceItem.EditTextPreference(
            preference = preferences.geminiModel,
            title = stringResource(SYMR.strings.pref_translation_gemini_model),
            subtitle = "Gemini model name (e.g. gemini-1.5-flash)",
        )
    }

    @Composable
    private fun openRouterApiKey(preferences: TranslationPreferences): Preference.PreferenceItem.EditTextPreference {
        return Preference.PreferenceItem.EditTextPreference(
            preference = preferences.openRouterApiKey,
            title = stringResource(SYMR.strings.pref_translation_openrouter_api_key),
            subtitle = "API key for OpenRouter",
        )
    }

    @Composable
    private fun openRouterModel(preferences: TranslationPreferences): Preference.PreferenceItem.EditTextPreference {
        return Preference.PreferenceItem.EditTextPreference(
            preference = preferences.openRouterModel,
            title = stringResource(SYMR.strings.pref_translation_openrouter_model),
            subtitle = "OpenRouter model name (e.g. google/gemini-flash-1.5)",
        )
    }

    @Composable
    private fun autoTranslateOnDownload(preferences: TranslationPreferences): Preference.PreferenceItem.SwitchPreference {
        return Preference.PreferenceItem.SwitchPreference(
            preference = preferences.autoTranslateOnDownload,
            title = stringResource(SYMR.strings.pref_translation_auto_on_download),
            subtitle = stringResource(SYMR.strings.pref_translation_auto_on_download_summary),
        )
    }
}
