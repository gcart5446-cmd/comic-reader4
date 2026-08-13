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
    val parentArchiveUri: String? = null,
    val tapZoneMode: String = "STANDARD",
    val volumeKeysEnabled: Boolean = true,
    val volumeKeysInverted: Boolean = false,
    val orientationLock: String = "DEFAULT",
    val dualPageSplit: Boolean = false,
    val colorFilter: String = "DEFAULT",
    val backgroundColor: Long = 0xFF000000,
    val brightness: Float = 1.0f
)
