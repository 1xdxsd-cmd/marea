package com.pego.music.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PegoDatabase : RoomDatabase() {
    abstract fun pegoDao(): PegoDao

    companion object {
        @Volatile
        private var INSTANCE: PegoDatabase? = null

        fun getInstance(context: Context): PegoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PegoDatabase::class.java,
                    "pego_music.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
