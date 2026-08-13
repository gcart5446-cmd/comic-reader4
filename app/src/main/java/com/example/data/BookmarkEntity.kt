package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comicUri: String,
    val pageIndex: Int,
    val title: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
