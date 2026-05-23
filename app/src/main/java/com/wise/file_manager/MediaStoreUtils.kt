package com.wise.file_manager

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

object MediaStoreUtils {

    fun getFilesByCategory(context: Context, filterMode: FilterMode): List<File> {
        val files = mutableListOf<File>()
        
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        
        val selection = when (filterMode) {
            FilterMode.Images -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
            FilterMode.Videos -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
            FilterMode.Audio -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}"
            FilterMode.Documents -> {
                val mimeTypes = listOf(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/epub+zip",
                    "text/plain",
                    "text/html"
                )
                val placeholders = mimeTypes.joinToString(",") { "?" }
                "${MediaStore.Files.FileColumns.MIME_TYPE} IN ($placeholders)"
            }
            FilterMode.Apks -> "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/vnd.android.package-archive' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.apk'"
            FilterMode.Archives -> {
                val extensions = listOf("zip", "rar", "7z", "tar", "gz", "bz2", "7zip")
                val selectionBuilder = StringBuilder()
                extensions.forEachIndexed { index, ext ->
                    selectionBuilder.append("${MediaStore.Files.FileColumns.DATA} LIKE '%.$ext'")
                    if (index < extensions.size - 1) selectionBuilder.append(" OR ")
                }
                selectionBuilder.toString()
            }
            else -> null
        }

        val selectionArgs = if (filterMode == FilterMode.Documents) {
            arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/epub+zip",
                "text/plain",
                "text/html"
            )
        } else null

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn)
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        files.add(file)
                    }
                }
            }
        }
        
        return files
    }
}
