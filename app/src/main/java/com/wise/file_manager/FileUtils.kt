package com.wise.file_manager

import java.io.File
import java.io.FileInputStream
import java.util.*

object FileUtils {
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun isImage(ext: String) = ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    fun isVideo(ext: String) = ext in listOf("mp4", "mkv", "webm", "avi", "3gp")
    fun isAudio(ext: String) = ext in listOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "amr")
    fun isPdf(ext: String) = ext == "pdf"
    fun isDocx(ext: String) = ext == "docx" || ext == "doc"
    fun isExcel(ext: String) = ext in listOf("xlsx", "xls", "ods")
    fun isCsv(ext: String) = ext == "csv"
    fun isDb(ext: String) = ext in listOf("db", "sqlite", "sqlite3", "sql")
    fun isApk(ext: String) = ext == "apk" || ext == "apks"
    fun isArchive(ext: String) = ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2")
    fun isText(ext: String) = ext in listOf(
        "txt", "log", "json", "xml", "html", "css", "js", "ts", "py", "kt", "java",
        "c", "cpp", "h", "hpp", "cs", "sh", "bat", "md", "csv", "yml", "yaml",
        "ini", "conf", "properties", "gradle", "sql", "php", "rb", "go", "rs",
        "swift", "dart", "lua", "jsonl", "env"
    )

    fun isLikelyText(file: File): Boolean {
        if (!file.exists() || file.isDirectory || file.length() == 0L) return false
        return try {
            val inputStream = FileInputStream(file)
            val buffer = ByteArray(1024)
            val read = inputStream.read(buffer)
            inputStream.close()
            if (read <= 0) return true
            for (i in 0 until read) {
                if (buffer[i].toInt() == 0) return false // Null byte = binary
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
