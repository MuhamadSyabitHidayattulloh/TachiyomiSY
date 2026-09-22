package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hippo.unifile.UniFile
import eu.kanade.domain.translation.service.TranslationPreferences
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.translation.engine.CanvasRenderer
import eu.kanade.tachiyomi.data.translation.engine.Inpainter
import eu.kanade.tachiyomi.data.translation.engine.OcrRecognizer
import eu.kanade.tachiyomi.data.translation.engine.TextDetector
import eu.kanade.tachiyomi.data.translation.engine.TextRegion
import eu.kanade.tachiyomi.data.translation.engine.TextTranslator
import eu.kanade.tachiyomi.data.translation.model.TranslationJob
import eu.kanade.tachiyomi.data.translation.model.TranslationProgress
import eu.kanade.tachiyomi.data.translation.model.TranslationStage
import eu.kanade.tachiyomi.data.translation.model.TranslationState
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileOutputStream

class TranslationManager(
    private val context: Context,
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var currentJob: Job? = null

    private val queue = mutableListOf<TranslationJob>()
    private val _statusMap = MutableStateFlow<Map<Long, TranslationProgress>>(emptyMap())
    val statusMap: StateFlow<Map<Long, TranslationProgress>> = _statusMap.asStateFlow()

    fun getStatus(chapterId: Long): TranslationProgress {
        return _statusMap.value[chapterId] ?: TranslationProgress()
    }

    fun isTranslated(chapter: Chapter, manga: Manga, source: Source?): Boolean {
        if (source == null) return false
        val chapterDir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.ogTitle,
            source,
        ) ?: return false
        return TranslationStorage.hasTranslation(chapterDir)
    }

    fun queueTranslation(manga: Manga, chapter: Chapter) {
        val chapterId = chapter.id
        if (queue.any { it.chapter.id == chapterId }) return

        val job = TranslationJob(manga, chapter)
        queue.add(job)
        updateStatus(chapterId) {
            TranslationProgress(state = TranslationState.QUEUED, logs = listOf("Queued for translation"))
        }
        processQueue()
    }

    fun cancelTranslation(chapterId: Long) {
        queue.removeAll { it.chapter.id == chapterId }
        if (currentJob != null && queue.firstOrNull()?.chapter?.id == chapterId) {
            currentJob?.cancel()
            currentJob = null
        }
        updateStatus(chapterId) {
            TranslationProgress(
                state = TranslationState.FAILED,
                errorMessage = "Translation canceled by user",
                logs = listOf("Translation canceled"),
            )
        }
        processQueue()
    }

    fun deleteTranslation(manga: Manga, chapter: Chapter, source: Source?): Boolean {
        if (source == null) return false
        val chapterDir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.ogTitle,
            source,
        ) ?: return false

        val deleted = TranslationStorage.deleteTranslation(chapterDir)
        if (deleted) {
            _statusMap.update { map -> map - chapter.id }
        }
        return deleted
    }

    @Synchronized
    private fun processQueue() {
        if (currentJob?.isActive == true || queue.isEmpty()) return

        val job = queue.first()
        currentJob = scope.launch {
            runTranslationJob(job)
            queue.remove(job)
            processQueue()
        }
    }

    private suspend fun runTranslationJob(job: TranslationJob) {
        val chapterId = job.chapter.id
        val source = sourceManager.get(job.manga.source)
        if (source == null) {
            updateStatus(chapterId) {
                TranslationProgress(
                    state = TranslationState.FAILED,
                    errorMessage = "Source not found",
                    logs = listOf("Error: Source not found"),
                )
            }
            return
        }

        val chapterDir = downloadProvider.findChapterDir(
            job.chapter.name,
            job.chapter.scanlator,
            job.chapter.url,
            job.manga.ogTitle,
            source,
        )

        if (chapterDir == null || !chapterDir.exists()) {
            updateStatus(chapterId) {
                TranslationProgress(
                    state = TranslationState.FAILED,
                    errorMessage = "Chapter directory not found",
                    logs = listOf("Error: Downloaded chapter folder not found"),
                )
            }
            return
        }

        val pageFiles = chapterDir.listFiles()
            ?.filter { file ->
                val name = file.name?.lowercase() ?: ""
                file.isFile && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))
            }
            ?.sortedBy { it.name }
            .orEmpty()

        if (pageFiles.isEmpty()) {
            updateStatus(chapterId) {
                TranslationProgress(
                    state = TranslationState.FAILED,
                    errorMessage = "No pages found in chapter folder",
                    logs = listOf("Error: No image files in chapter folder"),
                )
            }
            return
        }

        val totalPages = pageFiles.size
        val logs = mutableListOf<String>()
        logs.add("Found $totalPages pages to translate")

        updateStatus(chapterId) {
            TranslationProgress(
                state = TranslationState.IN_PROGRESS,
                stage = TranslationStage.DETECTION,
                currentPage = 0,
                totalPages = totalPages,
                logs = logs.toList(),
            )
        }

        val targetDir = TranslationStorage.getTranslationDir(chapterDir, createIfMissing = true)
        if (targetDir == null) {
            updateStatus(chapterId) {
                TranslationProgress(
                    state = TranslationState.FAILED,
                    errorMessage = "Failed to create translations folder",
                    logs = listOf("Error: Cannot create translations subfolder"),
                )
            }
            return
        }

        val fromLang = preferences.translateFrom.get()
        val toLang = preferences.translateTo.get()
        val fontName = preferences.readerFont.get()

        for ((index, pageFile) in pageFiles.withIndex()) {
            val pageNum = index + 1
            logs.add("--- Processing Page $pageNum / $totalPages ---")

            try {
                val inputStream = pageFile.openInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap == null) {
                    logs.add("Page $pageNum: Failed to load bitmap")
                    continue
                }

                // Stage 1: Detection
                updateStatus(chapterId) {
                    TranslationProgress(
                        state = TranslationState.IN_PROGRESS,
                        stage = TranslationStage.DETECTION,
                        currentPage = pageNum,
                        totalPages = totalPages,
                        logs = logs.toList(),
                    )
                }
                logs.add("Page $pageNum: Detecting text areas...")
                val rawRegions = TextDetector.detectTextRegions(bitmap)
                logs.add("Page $pageNum: Found ${rawRegions.size} candidate text region(s)")

                // Stage 2: OCR
                updateStatus(chapterId) {
                    TranslationProgress(
                        state = TranslationState.IN_PROGRESS,
                        stage = TranslationStage.OCR,
                        currentPage = pageNum,
                        totalPages = totalPages,
                        logs = logs.toList(),
                    )
                }
                logs.add("Page $pageNum: Performing OCR ($fromLang)...")
                val textRegions = OcrRecognizer.recognizeText(bitmap, rawRegions, fromLang)
                logs.add("Page $pageNum: Recognized ${textRegions.size} text block(s)")

                if (textRegions.isEmpty()) {
                    // Save original if no text detected
                    saveBitmapToUniFile(bitmap, targetDir, pageFile.name ?: "page_$pageNum.png")
                    bitmap.recycle()
                    logs.add("Page $pageNum: No text detected, copied original")
                    continue
                }

                // Stage 3: Cleaning / Inpainting
                updateStatus(chapterId) {
                    TranslationProgress(
                        state = TranslationState.IN_PROGRESS,
                        stage = TranslationStage.CLEANING,
                        currentPage = pageNum,
                        totalPages = totalPages,
                        logs = logs.toList(),
                    )
                }
                logs.add("Page $pageNum: Cleaning text background...")
                val cleanedBitmap = Inpainter.inpaint(bitmap, textRegions)

                // Stage 4: Translation
                updateStatus(chapterId) {
                    TranslationProgress(
                        state = TranslationState.IN_PROGRESS,
                        stage = TranslationStage.TRANSLATION,
                        currentPage = pageNum,
                        totalPages = totalPages,
                        logs = logs.toList(),
                    )
                }
                logs.add("Page $pageNum: Translating text to $toLang...")
                val originalTexts = textRegions.map { it.text }
                val translatedTexts = TextTranslator.translateTexts(originalTexts, fromLang, toLang, preferences)

                val translatedRegions = textRegions.mapIndexed { i, region ->
                    TextRegion(region.rect, translatedTexts.getOrElse(i) { region.text })
                }

                // Stage 5: Canvas Render
                updateStatus(chapterId) {
                    TranslationProgress(
                        state = TranslationState.IN_PROGRESS,
                        stage = TranslationStage.CANVAS_RENDER,
                        currentPage = pageNum,
                        totalPages = totalPages,
                        logs = logs.toList(),
                    )
                }
                logs.add("Page $pageNum: Redrawing canvas with translated text...")
                val finalBitmap = CanvasRenderer.renderTranslation(cleanedBitmap, translatedRegions, fontName)

                // Save translated page
                saveBitmapToUniFile(finalBitmap, targetDir, pageFile.name ?: "page_$pageNum.png")

                bitmap.recycle()
                cleanedBitmap.recycle()
                finalBitmap.recycle()

                logs.add("Page $pageNum: Finished successfully")
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed translating page $pageNum" }
                logs.add("Page $pageNum Error: ${e.localizedMessage}")
            }
        }

        logs.add("Translation complete for chapter!")
        updateStatus(chapterId) {
            TranslationProgress(
                state = TranslationState.COMPLETED,
                stage = TranslationStage.IDLE,
                currentPage = totalPages,
                totalPages = totalPages,
                logs = logs.toList(),
            )
        }
    }

    private fun saveBitmapToUniFile(bitmap: Bitmap, dir: UniFile, filename: String) {
        val file = dir.createFile(filename) ?: return
        val outputStream = file.openOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, outputStream)
        outputStream.flush()
        outputStream.close()
    }

    private fun updateStatus(chapterId: Long, block: (TranslationProgress) -> TranslationProgress) {
        _statusMap.update { map ->
            val current = map[chapterId] ?: TranslationProgress()
            map + (chapterId to block(current))
        }
    }
}
