package com.example.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ComicEntity
import com.example.repository.ComicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val recentComics: List<ComicEntity> = emptyList(),
    val allComics: List<ComicEntity> = emptyList(),
    val favoriteComics: List<ComicEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: String = "ALL", // "ALL", "CBZ", "CBR", "PDF", "FAVORITES"
    val isLoading: Boolean = false
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    val repository = ComicRepository(application)

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow("ALL")
    private val _isLoading = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            // Clean up any previously generated sample comics if they exist in DB
            try {
                val db = AppDatabase.getDatabase(application)
                val all = db.comicDao().getAllComics().first()
                all.filter { it.uri.contains("sample_comics") }.forEach { sampleComic ->
                    db.comicDao().deleteComic(sampleComic.uri)
                }
            } catch (_: Exception) {}
        }
    }

    private val _filterState = combine(_searchQuery, _selectedFilter, _isLoading) { query, filter, loading ->
        Triple(query, filter, loading)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.recentComics,
        repository.allComics,
        repository.favoriteComics,
        _filterState
    ) { recents, all, favorites, (query, filter, loading) ->
        val filteredAll = all.filter { comic ->
            val matchesQuery = query.isEmpty() || comic.title.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                "CBZ" -> comic.fileFormat == "CBZ" || comic.fileFormat == "ZIP"
                "CBR" -> comic.fileFormat == "CBR" || comic.fileFormat == "RAR"
                "PDF" -> comic.fileFormat == "PDF"
                "FAVORITES" -> comic.isFavorite
                else -> true
            }
            matchesQuery && matchesFilter
        }

        LibraryUiState(
            recentComics = recents,
            allComics = filteredAll,
            favoriteComics = favorites,
            searchQuery = query,
            selectedFilter = filter,
            isLoading = loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelected(filter: String) {
        _selectedFilter.value = filter
    }

    fun toggleFavorite(comic: ComicEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(comic.uri, !comic.isFavorite)
        }
    }

    fun deleteComic(comic: ComicEntity) {
        viewModelScope.launch {
            repository.deleteComic(comic.uri)
        }
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.loadComicPages(uri.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
