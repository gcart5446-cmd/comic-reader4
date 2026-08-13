package com.example.ui.explorer

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.repository.ComicRepository
import com.example.util.SampleComicGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeString: String,
    val formatBadge: String?,
    val isComicFile: Boolean
)

data class ExplorerUiState(
    val currentPath: File = Environment.getExternalStorageDirectory(),
    val items: List<FileItem> = emptyList(),
    val breadcrumbs: List<File> = emptyList(),
    val containsImages: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {
    val repository = ComicRepository(application)

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState

    private val comicExtensions = setOf(
        "cbz", "cbr", "cb7", "cbt", "pdf", "zip", "rar", "7z", "tar",
        "jpg", "jpeg", "png", "webp", "bin"
    )

    init {
        viewModelScope.launch {
            // Pre-generate sample PDF and CBZ files into Downloads/Documents/App files
            SampleComicGenerator.checkAndGenerateSampleComics(getApplication())
            // Default to Download folder if it has files, otherwise External Storage Root
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val initialDir = if (downloads != null && downloads.exists() && (downloads.listFiles()?.isNotEmpty() == true)) {
                downloads
            } else {
                Environment.getExternalStorageDirectory()
            }
            loadDirectory(initialDir)
        }
    }

    fun generateSampleFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            SampleComicGenerator.checkAndGenerateSampleComics(getApplication())
            loadDirectory(_uiState.value.currentPath)
        }
    }

    fun openDownloads() {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads != null) loadDirectory(downloads)
    }

    fun openDocuments() {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (docs != null) loadDirectory(docs)
    }

    fun openAppStorage() {
        val appFiles = getApplication<Application>().getExternalFilesDir(null)
            ?: getApplication<Application>().filesDir
        loadDirectory(appFiles)
    }

    fun openStorageRoot() {
        loadDirectory(Environment.getExternalStorageDirectory())
    }

    fun loadDirectory(directory: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            withContext(Dispatchers.IO) {
                try {
                    val root = Environment.getExternalStorageDirectory()
                    val targetDir = if (directory.exists() && directory.isDirectory) {
                        directory
                    } else {
                        root
                    }

                    val files = targetDir.listFiles()?.filter { !it.isHidden } ?: emptyList()

                    val items = files.map { file ->
                        val ext = file.extension.lowercase(Locale.ROOT)
                        val isDir = file.isDirectory
                        val isKnownExt = comicExtensions.contains(ext)
                        // All non-directory files are openable!
                        val isComic = !isDir && file.length() > 0

                        val badgeText = when {
                            isDir -> null
                            ext.isNotEmpty() -> ext.uppercase(Locale.ROOT)
                            else -> "FILE"
                        }

                        FileItem(
                            file = file,
                            name = file.name,
                            isDirectory = isDir,
                            sizeString = if (isDir) "${file.listFiles()?.size ?: 0} items" else formatFileSize(file.length()),
                            formatBadge = badgeText,
                            isComicFile = isComic
                        )
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))

                    val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "avif", "jxl")
                    val hasImages = files.any { it.isFile && imageExts.contains(it.extension.lowercase(Locale.ROOT)) }

                    // Build breadcrumbs
                    val crumbs = mutableListOf<File>()
                    var current: File? = targetDir
                    while (current != null) {
                        crumbs.add(0, current)
                        current = current.parentFile
                        if (current != null && current.path == root.parentFile?.path) break
                    }

                    _uiState.value = ExplorerUiState(
                        currentPath = targetDir,
                        items = items,
                        breadcrumbs = if (crumbs.isEmpty()) listOf(targetDir) else crumbs,
                        containsImages = hasImages,
                        isLoading = false
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Cannot access directory: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun navigateUp() {
        val parent = _uiState.value.currentPath.parentFile
        val root = Environment.getExternalStorageDirectory()
        if (parent != null && parent.path.startsWith(root.path)) {
            loadDirectory(parent)
        }
    }

    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.ROOT, "%.2f GB", gb)
            mb >= 1 -> String.format(Locale.ROOT, "%.1f MB", mb)
            kb >= 1 -> String.format(Locale.ROOT, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
