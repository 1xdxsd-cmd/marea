package com.pego.music

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pego.music.core.network.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import java.io.File

class PegoApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkHttpDownloader.getInstance(), Localization("es", "ES"))
        createNotificationChannels()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(OkHttpDownloader.getInstance().client)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(120L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val downloadChannel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_desc)
            }
            manager.createNotificationChannel(downloadChannel)
        }
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "pego_downloads"
    }
}
