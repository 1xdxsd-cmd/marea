package com.marea.music.data.audio

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Hardware-accelerated native audio trimmer using Jetpack Media3 Transformer (zero FFmpeg bloat).
 */
class AudioTrimmer(private val context: Context) {

    @OptIn(UnstableApi::class)
    suspend fun trimAudio(
        inputUri: Uri,
        startMs: Long,
        endMs: Long,
        outputFile: File
    ): File = withContext(Dispatchers.Main) {
        require(endMs > startMs) { "El tiempo final debe ser mayor al inicial" }
        if (outputFile.exists()) outputFile.delete()

        suspendCancellableCoroutine { continuation ->
            val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs.coerceAtLeast(0L))
                .setEndPositionMs(endMs)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(clippingConfig)
                .build()

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .build()

            val sequence = EditedMediaItemSequence.Builder(editedMediaItem).build()
            val composition = Composition.Builder(sequence).build()

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) {
                            continuation.resume(outputFile)
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(exportException)
                        }
                    }
                })
                .build()

            continuation.invokeOnCancellation {
                runCatching { transformer.cancel() }
            }

            transformer.start(composition, outputFile.absolutePath)
        }
    }
}
