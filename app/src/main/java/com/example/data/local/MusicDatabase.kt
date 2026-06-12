package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SongEntity::class], version = 2, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {
    abstract val songDao: SongDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "myt_music_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
