package com.wise.file_manager

import android.content.Context
import android.os.Environment
import com.wise.file_manager.db.AppDatabase
import com.wise.file_manager.db.FileMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.fileDao()

    suspend fun getFilesForPath(path: String, forceRefresh: Boolean = false): List<File> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = dao.getFilesByParent(path)
            if (cached.isNotEmpty()) {
                return@withContext cached.map { File(it.path) }
            }
        }
        
        // Load from disk and update index
        val directory = File(path)
        val rawList = directory.listFiles() ?: emptyArray()
        val fileList = rawList.toList()
        indexFiles(path, fileList)
        return@withContext fileList
    }

    suspend fun getCachedMetadata(path: String): FileMetadata? = withContext(Dispatchers.IO) {
        dao.getFileByPath(path)
    }

    suspend fun indexFiles(parentPath: String, files: List<File>) = withContext(Dispatchers.IO) {
        val metadataList = files.map { file ->
            var childCount = ""
            if (file.isDirectory) {
                try {
                    val allItems = file.listFiles()
                    val f = allItems?.count { it.isFile } ?: 0
                    val d = allItems?.count { it.isDirectory } ?: 0
                    childCount = "$f files"
                } catch (e: Exception) {}
            }
            FileMetadata(
                file.absolutePath,
                parentPath,
                file.name,
                file.isDirectory,
                file.length(),
                file.lastModified(),
                childCount
            )
        }
        dao.deleteByParent(parentPath)
        dao.insertAll(metadataList)
    }

    suspend fun fullIndex() = withContext(Dispatchers.IO) {
        if (dao.count > 100) return@withContext // Already has a decent index
        
        val root = Environment.getExternalStorageDirectory()
        // Index the top-level directories thoroughly
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val files = dir.listFiles()?.toList() ?: emptyList()
            indexFiles(dir.absolutePath, files)
            
            // Go one level deeper for common folders like Download, DCIM
            if (dir.name in listOf("Download", "DCIM", "Documents", "Pictures", "Music", "Movies")) {
                dir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                    val subFiles = subDir.listFiles()?.toList() ?: emptyList()
                    indexFiles(subDir.absolutePath, subFiles)
                }
            }
        }
    }
    
    suspend fun clearIndex() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    suspend fun search(query: String): List<File> = withContext(Dispatchers.IO) {
        dao.search("%$query%").map { File(it.path) }
    }
}
