package com.example.repository

import android.content.Context
import android.net.Uri
import com.example.data.AppDatabase
import com.example.data.BookmarkEntity
import com.example.data.ComicEntity
import com.example.parser.ComicPagesResult
import com.example.parser.ComicParser
import kotlinx.coroutines.flow.Flow
import java.io.File

class ComicRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val comicDao = db.comicDao()

    val allComics: Flow<List<ComicEntity>> = comicDao.getAllComics()
    val recentComics: Flow<List<ComicEntity>> = comicDao.getRecentComics()
    val favoriteComics: Flow<List<ComicEntity>> = comicDao.getFavoriteComics()

    fun getComicByUri(uri: String): Flow<ComicEntity?> = comicDao.getComicByUri(uri)

    suspend fun loadComicPages(uriString: String): ComicPagesResult {
        val uri = Uri.parse(uriString)
        val result = ComicParser.parseAndExtract(context, uri)

        val existing = comicDao.getComicByUriSync(uriString)
        val entity = ComicEntity(
            uri = uriString,
            title = existing?.title ?: result.title,
            fileFormat = result.format,
            coverUri = result.coverFile?.absolutePath ?: existing?.coverUri,
            totalPages = result.pageFiles.size,
            lastReadPage = existing?.lastReadPage ?: 0,
            lastReadTime = System.currentTimeMillis(),
            isFavorite = existing?.isFavorite ?: false,
            readingDirection = existing?.readingDirection ?: "LTR",
            scaleType = existing?.scaleType ?: "FIT_SCREEN",
            scrollMode = existing?.scrollMode ?: "PAGER",
            fileSize = existing?.fileSize ?: 0L
        )
        comicDao.insertOrUpdate(entity)

        return result
    }

    suspend fun saveProgress(uri: String, pageIndex: Int, totalPages: Int) {
        comicDao.updateProgress(uri, pageIndex, totalPages, System.currentTimeMillis())
    }

    suspend fun toggleFavorite(uri: String, isFavorite: Boolean) {
        comicDao.setFavorite(uri, isFavorite)
    }

    suspend fun saveReaderSettings(
        uri: String,
        direction: String,
        scaleType: String,
        scrollMode: String
    ) {
        comicDao.updateReaderSettings(uri, direction, scaleType, scrollMode)
    }

    suspend fun deleteComic(uri: String) {
        comicDao.deleteComic(uri)
    }

    // Bookmarks
    fun getBookmarks(comicUri: String): Flow<List<BookmarkEntity>> =
        comicDao.getBookmarksForComic(comicUri)

    suspend fun addBookmark(comicUri: String, pageIndex: Int, title: String, note: String = "") {
        comicDao.insertBookmark(
            BookmarkEntity(
                comicUri = comicUri,
                pageIndex = pageIndex,
                title = title,
                note = note,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteBookmark(id: Long) {
        comicDao.deleteBookmark(id)
    }
}
