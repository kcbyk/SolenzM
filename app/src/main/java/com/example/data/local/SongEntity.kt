package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val streamUrl: String,
    val localFilePath: String? = null,
    val coverUrl: String,
    val downloadStatus: Int, // 0 = Not Downloaded, 1 = Downloading, 2 = Downloaded
    val downloadType: String? = null, // "mp3" or "mp4"
    val isFavorite: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
