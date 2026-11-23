package com.marea.music.data.local

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
abstract class MareaDatabase : RoomDatabase() {
    abstract fun mareaDao(): MareaDao

    companion object {
        @Volatile
        private var INSTANCE: MareaDatabase? = null

        fun getInstance(context: Context): MareaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MareaDatabase::class.java,
                    "marea_music.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
