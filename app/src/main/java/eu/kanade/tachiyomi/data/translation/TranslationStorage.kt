package eu.kanade.tachiyomi.data.translation

import com.hippo.unifile.UniFile

object TranslationStorage {

    fun getTranslationDir(chapterDir: UniFile, createIfMissing: Boolean = false): UniFile? {
        return if (chapterDir.isFile) {
            val parent = chapterDir.parentFile ?: return null
            val baseName = chapterDir.name?.substringBeforeLast(".") ?: "chapter"
            if (createIfMissing) {
                val translationsFolder = parent.createDirectory("translations") ?: parent
                translationsFolder.createDirectory(baseName)
            } else {
                parent.findFile("translations")?.findFile(baseName)
                    ?: parent.findFile("${baseName}_translations")
            }
        } else if (chapterDir.isDirectory) {
            if (createIfMissing) {
                chapterDir.createDirectory("translations")
            } else {
                chapterDir.findFile("translations")
            }
        } else {
            null
        }
    }

    fun hasTranslation(chapterDir: UniFile): Boolean {
        val transDir = getTranslationDir(chapterDir, createIfMissing = false) ?: return false
        val files = transDir.listFiles() ?: return false
        return files.any { it.isFile && (it.name?.endsWith(".jpg") == true || it.name?.endsWith(".png") == true || it.name?.endsWith(".webp") == true) }
    }

    fun getTranslatedPageFile(chapterDir: UniFile, pageName: String): UniFile? {
        val transDir = getTranslationDir(chapterDir, createIfMissing = false) ?: return null
        return transDir.findFile(pageName)
            ?: transDir.findFile(pageName.substringBeforeLast(".") + ".png")
            ?: transDir.findFile(pageName.substringBeforeLast(".") + ".jpg")
    }

    fun deleteTranslation(chapterDir: UniFile): Boolean {
        val transDir = getTranslationDir(chapterDir, createIfMissing = false) ?: return false
        return transDir.delete()
    }
}
