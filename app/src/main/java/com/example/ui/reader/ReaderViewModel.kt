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
import com.example.repository.ComicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File


data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

data class ReaderSettingsState(
    val readingDirection: String = "LTR",
    val scaleType: String = "FIT_SCREEN",
    val scrollMode: String = "PAGER",
    val isCropMarginsEnabled: Boolean = false,
    val backgroundColor: Long = 0xFF000000,
    val brightness: Float = 1.0f,
    val isControlsVisible: Boolean = true,
    val colorFilter: String = "DEFAULT"
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
    val colorFilter: String = "DEFAULT"
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

    private val _readerSettings = combine(_readerDisplayState, _readerAtmosphereState) { display, atmosphere ->
        ReaderSettingsState(
            readingDirection = display.first,
            scaleType = display.second,
            scrollMode = display.third,
            isCropMarginsEnabled = display.fourth,
            backgroundColor = atmosphere.first,
            brightness = atmosphere.second,
            isControlsVisible = atmosphere.third,
            colorFilter = atmosphere.fourth
        )
    }

    val uiState: StateFlow<ReaderUiState> = combine(
        _parsedResult,
        _currentPageIndex,
        _readerSettings,
        comicEntity,
        bookmarks
    ) { parsed, pageIdx, settings, entity, bmarks ->
        ReaderUiState(
            comicUri = comicUri,
            title = entity?.title ?: parsed?.title ?: "Comic",
            format = entity?.fileFormat ?: parsed?.format ?: "CBZ",
            pages = parsed?.pageFiles ?: emptyList(),
            totalPages = parsed?.pageFiles?.size ?: entity?.totalPages ?: 0,
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
            isLoading = _isLoading.value,
            errorMessage = _errorMessage.value,
            colorFilter = settings.colorFilter
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
                val result = repository.loadComicPages(comicUri)
                _parsedResult.value = result

                val entity = AppDatabase.getDatabase(getApplication()).comicDao().getComicByUriSync(comicUri)
                val savedPage = entity?.lastReadPage ?: 0
                val total = result.pageFiles.size
                if (total == 0) {
                    _errorMessage.value = "No readable pages found in this comic file."
                } else {
                    val validPage = if (savedPage in 0 until total) savedPage else 0
                    _currentPageIndex.value = validPage
                    _readingDirection.value = entity?.readingDirection ?: "LTR"
                    _scaleType.value = entity?.scaleType ?: "FIT_SCREEN"
                    _scrollMode.value = entity?.scrollMode ?: "PAGER"
                    
                    prefetchPages(validPage, result.pageFiles)
                }

            } catch (e: Exception) {
                _errorMessage.value = "Failed to open comic: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
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
    }

    fun setBrightness(value: Float) {
        _brightness.value = value.coerceIn(0.1f, 1.0f)
    }

    fun setColorFilter(filter: String) {
        _colorFilter.value = filter
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
                scrollMode = _scrollMode.value
            )
        }
    }
}

