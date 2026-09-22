package eu.kanade.tachiyomi.data.translation.model

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

enum class TranslationState {
    NOT_TRANSLATED,
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
}

enum class TranslationStage {
    IDLE,
    DETECTION,
    OCR,
    CLEANING,
    TRANSLATION,
    CANVAS_RENDER,
}

data class TranslationProgress(
    val state: TranslationState = TranslationState.NOT_TRANSLATED,
    val stage: TranslationStage = TranslationStage.IDLE,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null,
) {
    val progressFraction: Float
        get() = if (totalPages > 0) currentPage.toFloat() / totalPages else 0f
}

data class TranslationJob(
    val manga: Manga,
    val chapter: Chapter,
    var progress: TranslationProgress = TranslationProgress(),
)
