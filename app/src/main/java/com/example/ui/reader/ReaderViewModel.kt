package com.example.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.request.ImageRequest
import com.example.data.AppDatabase
import com.example.data.BookmarkEntity
import com.example.data.ComicEntity
import com.example.parser.ComicPagesResult
import com.example.parser.DualPageSplitter
import com.example.repository.ComicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File


import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.util.Log
import androidx.core.content.FileProvider

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

data class ReaderSettingsState(
    val readingDirection: String = "LTR",
    val scaleType: String = "FIT_SCREEN",
    val scrollMode: String = "PAGER",
    val isCropMarginsEnabled: Boolean = false,
    val backgroundColor: Long = 0xFF000000,
    val brightness: Float = 1.0f,
    val isControlsVisible: Boolean = true,
    val colorFilter: String = "DEFAULT",
    val tapZoneMode: String = "STANDARD",
    val volumeKeysEnabled: Boolean = true,
    val volumeKeysInverted: Boolean = false,
    val orientationLock: String = "DEFAULT",
    val dualPageSplit: Boolean = false
)

data class ReaderUiState(
    val comicUri: String = "",
    val title: String = "",
    val format: String = "",
    val pages: List<File> = emptyList(),
    val totalPages: Int = 0,
    val currentPageIndex: Int = 0,
    val readingDirection: String = "LTR",
    val scaleType: String = "FIT_SCREEN",
    val scrollMode: String = "PAGER",
    val isCropMarginsEnabled: Boolean = false,
    val backgroundColor: Long = 0xFF000000,
    val brightness: Float = 1.0f,
    val isControlsVisible: Boolean = true,
    val isFavorite: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val colorFilter: String = "DEFAULT",
    val tapZoneMode: String = "STANDARD",
    val volumeKeysEnabled: Boolean = true,
    val volumeKeysInverted: Boolean = false,
    val orientationLock: String = "DEFAULT",
    val dualPageSplit: Boolean = false
)

class ReaderViewModel(
    application: Application,
    private val comicUri: String
) : AndroidViewModel(application) {
    val repository = ComicRepository(application)

    private val _currentPageIndex = MutableStateFlow(0)
    private val _readingDirection = MutableStateFlow("LTR")
    private val _scaleType = MutableStateFlow("FIT_SCREEN")
    private val _scrollMode = MutableStateFlow("PAGER")
    private val _isCropMarginsEnabled = MutableStateFlow(false)
    private val _backgroundColor = MutableStateFlow(0xFF000000)
    private val _brightness = MutableStateFlow(1.0f)
    private val _colorFilter = MutableStateFlow("DEFAULT")
    private val _isControlsVisible = MutableStateFlow(true)
    private val _isLoading = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _parsedResult = MutableStateFlow<ComicPagesResult?>(null)

    private val _tapZoneMode = MutableStateFlow("STANDARD")
    private val _volumeKeysEnabled = MutableStateFlow(true)
    private val _volumeKeysInverted = MutableStateFlow(false)
    private val _orientationLock = MutableStateFlow("DEFAULT")
    private val _dualPageSplit = MutableStateFlow(false)

    val comicEntity: StateFlow<ComicEntity?> = repository.getComicByUri(comicUri)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.getBookmarks(comicUri)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _readerDisplayState = combine(_readingDirection, _scaleType, _scrollMode, _isCropMarginsEnabled) { dir, scale, scroll, crop ->
        Quadruple(dir, scale, scroll, crop)
    }

    private val _readerAtmosphereState = combine(_backgroundColor, _brightness, _isControlsVisible, _colorFilter) { bg, bright, controls, filter ->
        Quadruple(bg, bright, controls, filter)
    }

    private val _readerBehaviorState = combine(_tapZoneMode, _volumeKeysEnabled, _volumeKeysInverted, _orientationLock) { tap, vol, inv, orient ->
        Quadruple(tap, vol, inv, orient)
    }

    private val _readerSettings = combine(_readerDisplayState, _readerAtmosphereState, _readerBehaviorState, _dualPageSplit) { display, atmosphere, behavior, dual ->
        ReaderSettingsState(
            readingDirection = display.first,
            scaleType = display.second,
            scrollMode = display.third,
            isCropMarginsEnabled = display.fourth,
            backgroundColor = atmosphere.first,
            brightness = atmosphere.second,
            isControlsVisible = atmosphere.third,
            colorFilter = atmosphere.fourth,
            tapZoneMode = behavior.first,
            volumeKeysEnabled = behavior.second,
            volumeKeysInverted = behavior.third,
            orientationLock = behavior.fourth,
            dualPageSplit = dual
        )
    }

    private val _processedPages: Flow<List<File>> = combine(_parsedResult, _dualPageSplit, _readingDirection) { parsed, dual, dir ->
        val rawPages = parsed?.pageFiles ?: emptyList()
        if (rawPages.isEmpty()) {
            emptyList()
        } else {
            DualPageSplitter.processPages(
                context = getApplication(),
                rawFiles = rawPages,
                isDualPageSplit = dual,
                isRtl = (dir == "RTL")
            )
        }
    }

    val uiState: StateFlow<ReaderUiState> = combine(
        _processedPages,
        _currentPageIndex,
        _readerSettings,
        comicEntity
    ) { pages, pageIdx, settings, entity ->
        val parsed = _parsedResult.value
        val loading = _isLoading.value
        val errorMsg = _errorMessage.value
        val bmarks = bookmarks.value

        ReaderUiState(
            comicUri = comicUri,
            title = entity?.title ?: parsed?.title ?: "Comic",
            format = entity?.fileFormat ?: parsed?.format ?: "CBZ",
            pages = pages,
            totalPages = pages.size.coerceAtLeast(entity?.totalPages ?: 0),
            currentPageIndex = pageIdx,
            readingDirection = settings.readingDirection,
            scaleType = settings.scaleType,
            scrollMode = settings.scrollMode,
            isCropMarginsEnabled = settings.isCropMarginsEnabled,
            backgroundColor = settings.backgroundColor,
            brightness = settings.brightness,
            isControlsVisible = settings.isControlsVisible,
            isFavorite = entity?.isFavorite ?: false,
            bookmarks = bmarks,
            isLoading = loading,
            errorMessage = errorMsg,
            colorFilter = settings.colorFilter,
            tapZoneMode = settings.tapZoneMode,
            volumeKeysEnabled = settings.volumeKeysEnabled,
            volumeKeysInverted = settings.volumeKeysInverted,
            orientationLock = settings.orientationLock,
            dualPageSplit = settings.dualPageSplit
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderUiState())

    init {
        loadComic()
    }

    private fun loadComic() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val entity = AppDatabase.getDatabase(getApplication()).comicDao().getComicByUriSync(comicUri)
                val savedPage = entity?.lastReadPage ?: 0

                var initialPageSet = false

                repository.loadComicPagesFlow(comicUri, savedPage).collect { result ->
                    _parsedResult.value = result

                    if (result.isPasswordProtected) {
                        _errorMessage.value = "This comic file is password-protected and cannot be opened."
                        _isLoading.value = false
                        return@collect
                    }

                    val total = result.pageFiles.size
                    if (total > 0) {
                        _isLoading.value = false
                        if (!initialPageSet) {
                            initialPageSet = true
                            val validPage = if (savedPage in 0 until total) savedPage else 0
                            if (_currentPageIndex.value != validPage) {
                                _currentPageIndex.value = validPage
                            }
                            _readingDirection.value = entity?.readingDirection ?: "LTR"
                            _scaleType.value = entity?.scaleType ?: "FIT_SCREEN"
                            _scrollMode.value = entity?.scrollMode ?: "PAGER"
                            _tapZoneMode.value = entity?.tapZoneMode ?: "STANDARD"
                            _volumeKeysEnabled.value = entity?.volumeKeysEnabled ?: true
                            _volumeKeysInverted.value = entity?.volumeKeysInverted ?: false
                            _orientationLock.value = entity?.orientationLock ?: "DEFAULT"
                            _dualPageSplit.value = entity?.dualPageSplit ?: false
                            _colorFilter.value = entity?.colorFilter ?: "DEFAULT"
                            _backgroundColor.value = entity?.backgroundColor ?: 0xFF000000
                            _brightness.value = entity?.brightness ?: 1.0f
                            prefetchPages(validPage, result.pageFiles)
                        } else {
                            prefetchPages(_currentPageIndex.value, result.pageFiles)
                        }
                    } else if (!result.isExtracting) {
                        _isLoading.value = false
                        _errorMessage.value = "No readable pages found in this comic file."
                    }
                }

            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = "Failed to open comic: ${e.localizedMessage}"
            }
        }
    }

    fun onPageChanged(pageIndex: Int) {
        val pages = _parsedResult.value?.pageFiles ?: emptyList()
        val total = pages.size
        if (pageIndex in 0 until total) {
            _currentPageIndex.value = pageIndex
            viewModelScope.launch {
                repository.saveProgress(comicUri, pageIndex, total)
            }
            prefetchPages(pageIndex, pages)
        }
    }

    private fun prefetchPages(currentIndex: Int, pages: List<File>) {
        if (pages.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val imageLoader = Coil.imageLoader(context)
            val indicesToPrefetch = listOf(
                currentIndex + 1,
                currentIndex + 2,
                currentIndex + 3,
                currentIndex - 1
            ).filter { it in pages.indices }

            for (idx in indicesToPrefetch) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(pages[idx])
                        .build()
                    imageLoader.enqueue(request)
                } catch (_: Exception) {}
            }
        }
    }

    fun toggleCropMargins() {
        _isCropMarginsEnabled.value = !_isCropMarginsEnabled.value
    }

    fun toggleControls() {
        _isControlsVisible.value = !_isControlsVisible.value
    }

    fun setReadingDirection(direction: String) {
        _readingDirection.value = direction
        saveSettings()
    }

    fun setScaleType(scaleType: String) {
        _scaleType.value = scaleType
        saveSettings()
    }

    fun setScrollMode(scrollMode: String) {
        _scrollMode.value = scrollMode
        saveSettings()
    }

    fun setBackgroundColor(colorLong: Long) {
        _backgroundColor.value = colorLong
        saveSettings()
    }

    fun setBrightness(value: Float) {
        _brightness.value = value.coerceIn(0.1f, 1.0f)
        saveSettings()
    }

    fun setColorFilter(filter: String) {
        _colorFilter.value = filter
        saveSettings()
    }

    fun setTapZoneMode(mode: String) {
        _tapZoneMode.value = mode
        saveSettings()
    }

    fun setVolumeKeysEnabled(enabled: Boolean) {
        _volumeKeysEnabled.value = enabled
        saveSettings()
    }

    fun setVolumeKeysInverted(inverted: Boolean) {
        _volumeKeysInverted.value = inverted
        saveSettings()
    }

    fun setOrientationLock(lock: String) {
        _orientationLock.value = lock
        saveSettings()
    }

    fun setDualPageSplit(enabled: Boolean) {
        _dualPageSplit.value = enabled
        saveSettings()
    }

    fun setPageAsCover(pageIndex: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val pages = uiState.value.pages
                if (pageIndex in pages.indices) {
                    val pageFile = pages[pageIndex]
                    if (pageFile.exists()) {
                        repository.setCover(comicUri, pageFile.absolutePath)
                        onResult(true, "Set page ${pageIndex + 1} as comic cover")
                    } else {
                        onResult(false, "Page file not found")
                    }
                } else {
                    onResult(false, "Invalid page index")
                }
            } catch (e: Exception) {
                onResult(false, "Error setting cover: ${e.message}")
            }
        }
    }

    fun getShareIntentForPage(context: Context, pageIndex: Int): Intent? {
        val pages = uiState.value.pages
        if (pageIndex !in pages.indices) return null
        val pageFile = pages[pageIndex]
        if (!pageFile.exists()) return null

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, pageFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun savePageToPictures(context: Context, pageIndex: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pages = uiState.value.pages
                if (pageIndex !in pages.indices) {
                    onResult(false, "Invalid page index")
                    return@launch
                }
                val pageFile = pages[pageIndex]
                if (!pageFile.exists()) {
                    onResult(false, "Page file not found")
                    return@launch
                }

                val cleanTitle = uiState.value.title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                val filename = "${cleanTitle}_Page_${pageIndex + 1}_${System.currentTimeMillis()}.jpg"
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ComicReader")
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            pageFile.inputStream().use { input -> input.copyTo(out) }
                        }
                        onResult(true, "Saved to Pictures/ComicReader")
                    } else {
                        onResult(false, "Failed to create MediaStore record")
                    }
                } else {
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val targetFolder = File(picturesDir, "ComicReader")
                    if (!targetFolder.exists()) targetFolder.mkdirs()
                    val targetFile = File(targetFolder, filename)
                    pageFile.copyTo(targetFile, overwrite = true)
                    onResult(true, "Saved to ${targetFile.absolutePath}")
                }
            } catch (e: Exception) {
                onResult(false, "Error saving page: ${e.message}")
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val currentFav = comicEntity.value?.isFavorite ?: false
            repository.toggleFavorite(comicUri, !currentFav)
        }
    }

    fun addBookmark(title: String, note: String = "") {
        viewModelScope.launch {
            repository.addBookmark(
                comicUri = comicUri,
                pageIndex = _currentPageIndex.value,
                title = if (title.isBlank()) "Page ${_currentPageIndex.value + 1}" else title,
                note = note
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }

    private fun saveSettings() {
        viewModelScope.launch {
            repository.saveReaderSettings(
                uri = comicUri,
                direction = _readingDirection.value,
                scaleType = _scaleType.value,
                scrollMode = _scrollMode.value,
                tapZoneMode = _tapZoneMode.value,
                volumeKeysEnabled = _volumeKeysEnabled.value,
                volumeKeysInverted = _volumeKeysInverted.value,
                orientationLock = _orientationLock.value,
                dualPageSplit = _dualPageSplit.value,
                colorFilter = _colorFilter.value,
                backgroundColor = _backgroundColor.value,
                brightness = _brightness.value
            )
        }
    }
}

