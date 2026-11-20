package com.pego.music.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object PegoMediaCache {
    private const val MAX_CACHE_BYTES = 150L * 1024L * 1024L

    @Volatile
    private var instance: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache {
        return instance ?: synchronized(this) {
            instance ?: SimpleCache(
                File(context.applicationContext.cacheDir, "media_cache"),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { instance = it }
        }
    }
}
