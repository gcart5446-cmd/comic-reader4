package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comics")
data class ComicEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val fileFormat: String,
    val coverUri: String? = null,
    val totalPages: Int = 0,
    val lastReadPage: Int = 0,
    val lastReadTime: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val readingDirection: String = "LTR", // "LTR", "RTL", "VERTICAL"
    val scaleType: String = "FIT_SCREEN",  // "FIT_SCREEN", "FIT_WIDTH", "FIT_HEIGHT"
    val scrollMode: String = "PAGER",      // "PAGER", "WEBTOON"
    val fileSize: Long = 0L,
    val parentArchiveUri: String? = null
)
