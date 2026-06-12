package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY timestamp DESC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE downloadStatus = 2 ORDER BY timestamp DESC")
    fun getDownloadedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Delete
    suspend fun deleteSong(song: SongEntity)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)

    @Query("UPDATE songs SET downloadStatus = :status, localFilePath = :localPath WHERE id = :id")
    suspend fun updateDownloadStatus(id: String, status: Int, localPath: String?)
}
