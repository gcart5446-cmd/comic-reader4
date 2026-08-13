package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicDao {
    @Query("SELECT * FROM comics ORDER BY lastReadTime DESC")
    fun getAllComics(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics WHERE lastReadPage > 0 ORDER BY lastReadTime DESC LIMIT 20")
    fun getRecentComics(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics WHERE isFavorite = 1 ORDER BY lastReadTime DESC")
    fun getFavoriteComics(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics WHERE uri = :uri")
    fun getComicByUri(uri: String): Flow<ComicEntity?>

    @Query("SELECT * FROM comics WHERE uri = :uri")
    suspend fun getComicByUriSync(uri: String): ComicEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(comic: ComicEntity)

    @Query("UPDATE comics SET lastReadPage = :page, totalPages = :totalPages, lastReadTime = :timestamp WHERE uri = :uri")
    suspend fun updateProgress(uri: String, page: Int, totalPages: Int, timestamp: Long)

    @Query("UPDATE comics SET isFavorite = :isFavorite WHERE uri = :uri")
    suspend fun setFavorite(uri: String, isFavorite: Boolean)

    @Query("UPDATE comics SET readingDirection = :direction, scaleType = :scaleType, scrollMode = :scrollMode WHERE uri = :uri")
    suspend fun updateReaderSettings(uri: String, direction: String, scaleType: String, scrollMode: String)

    @Query("DELETE FROM comics WHERE uri = :uri")
    suspend fun deleteComic(uri: String)

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE comicUri = :comicUri ORDER BY pageIndex ASC")
    fun getBookmarksForComic(comicUri: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)
}
