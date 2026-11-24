package com.marea.music.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.marea.music.core.network.OkHttpDownloader

/**
 * Baja el inicio de la siguiente canción a la caché del reproductor, así al saltar
 * (también desde los audífonos) empieza a sonar casi al instante.
 */
@OptIn(UnstableApi::class)
object AudioPrecache {
    private const val PRECACHE_BYTES = 700L * 1024L

    /** Bloquea el hilo: llamarlo desde Dispatchers.IO. */
    fun precache(context: Context, url: String) {
        if (!url.startsWith("http")) return
        val upstream = OkHttpDataSource.Factory(OkHttpDownloader.getInstance().client)
            .setUserAgent(OkHttpDownloader.USER_AGENT)
        val dataSource = CacheDataSource.Factory()
            .setCache(MareaMediaCache.getInstance(context))
            .setUpstreamDataSourceFactory(upstream)
            .createDataSource()
        val spec = DataSpec.Builder()
            .setUri(Uri.parse(url))
            .setPosition(0)
            .setLength(PRECACHE_BYTES)
            .build()
        runCatching { CacheWriter(dataSource, spec, null, null).cache() }
    }
}
