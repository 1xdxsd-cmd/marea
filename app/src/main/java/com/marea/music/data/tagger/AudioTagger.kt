package com.marea.music.data.tagger

import android.content.Context
import android.net.Uri
import com.marea.music.domain.model.EnrichedMetadata
import com.marea.music.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class SavedAudioResult(
    val localUri: String,
    val localFilePath: String?
)

class AudioTagger(private val context: Context) {

    /**
     * Pone título, artista y portada al .m4a y lo guarda en la carpeta privada de la app.
     * Así las descargas solo se escuchan en Marea y no aparecen en otros reproductores.
     */
    suspend fun tagAndSaveM4a(
        tempAudioFile: File,
        coverFile: File?,
        metadata: EnrichedMetadata,
        trackId: String = ""
    ): SavedAudioResult = withContext(Dispatchers.IO) {
        applyTags(tempAudioFile, coverFile, metadata)
        val baseName = trackId.ifBlank { sanitizeFilename("${metadata.artist} - ${metadata.title}") }
        exportToPrivateStorage(tempAudioFile, sanitizeFilename(baseName) + ".m4a")
    }

    /**
     * Replaces the embedded cover art (or updates trimmed audio) for an already-downloaded track,
     * preserving existing tags and persisting a local cover file in app filesDir.
     */
    suspend fun retagAndReplaceAudio(
        track: TrackItem,
        sourceAudioFile: File,
        newCoverFile: File?
    ): Pair<SavedAudioResult, String?> = withContext(Dispatchers.IO) {
        val coverToEmbed = newCoverFile ?: extractExistingCover(track)
        val metadata = EnrichedMetadata(
            title = track.cleanTitle.ifBlank { track.title },
            artist = track.artist,
            album = track.album,
            year = track.year,
            coverUrl = track.bestCoverUrl
        )
        applyTags(sourceAudioFile, coverToEmbed, metadata)

        val saved = overwriteOrExportTrack(track, sourceAudioFile, metadata)

        val persistedCoverUri = if (newCoverFile != null && newCoverFile.exists()) {
            val coverDir = File(context.filesDir, "covers").apply { mkdirs() }
            val destCover = File(coverDir, "${track.id}_${System.currentTimeMillis()}.jpg")
            newCoverFile.copyTo(destCover, overwrite = true)
            destCover.toURI().toString()
        } else {
            null
        }

        saved to persistedCoverUri
    }

    suspend fun copyUriToTempFile(uriString: String, prefix: String, ext: String): File? =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriString)
            val tempFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.$ext")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext null
                if (tempFile.length() > 0L) tempFile else {
                    tempFile.delete()
                    null
                }
            }.getOrElse {
                tempFile.delete()
                null
            }
        }

    private fun extractExistingCover(track: TrackItem): File? {
        val localSource = track.localFilePath?.let(::File)?.takeIf { it.exists() }
            ?: track.localUri?.let { uriStr ->
                runCatching {
                    val temp = File(context.cacheDir, "orig_audio_${track.id}.m4a")
                    context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    temp.takeIf { it.exists() && it.length() > 0L }
                }.getOrNull()
            } ?: return null

        return runCatching {
            val audioFile = AudioFileIO.read(localSource)
            val bytes = audioFile.tag?.firstArtwork?.binaryData ?: return@runCatching null
            val out = File(context.cacheDir, "extracted_cover_${track.id}.jpg")
            out.writeBytes(bytes)
            out
        }.getOrNull()
    }

    private fun applyTags(
        audioFileTarget: File,
        coverFile: File?,
        metadata: EnrichedMetadata
    ) {
        runCatching {
            val audioFile = AudioFileIO.read(audioFileTarget)
            val tag = audioFile.tagOrCreateAndSetDefault
            if (metadata.title.isNotBlank()) tag.setField(FieldKey.TITLE, metadata.title)
            if (metadata.artist.isNotBlank()) tag.setField(FieldKey.ARTIST, metadata.artist)
            if (metadata.album.isNotBlank()) tag.setField(FieldKey.ALBUM, metadata.album)
            if (metadata.year.isNotBlank()) tag.setField(FieldKey.YEAR, metadata.year)

            if (coverFile != null && coverFile.exists() && coverFile.length() > 0) {
                val artwork = ArtworkFactory.createArtworkFromFile(coverFile)
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
            audioFile.commit()
        }
    }

    private fun overwriteOrExportTrack(
        track: TrackItem,
        sourceFile: File,
        metadata: EnrichedMetadata
    ): SavedAudioResult {
        val existingUri = track.localUri?.takeIf { it.startsWith("content://") }?.let(Uri::parse)
        if (existingUri != null) {
            val written = runCatching {
                context.contentResolver.openOutputStream(existingUri, "wt")?.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                } != null
            }.getOrDefault(false)
            if (written) {
                return SavedAudioResult(localUri = existingUri.toString(), localFilePath = track.localFilePath)
            }
        }

        val existingPath = track.localFilePath?.let(::File)
        if (existingPath != null && existingPath.parentFile?.exists() == true) {
            sourceFile.copyTo(existingPath, overwrite = true)
            return SavedAudioResult(
                localUri = existingPath.toURI().toString(),
                localFilePath = existingPath.absolutePath
            )
        }

        return exportToPrivateStorage(sourceFile, sanitizeFilename(track.id) + ".m4a")
    }

    private fun exportToPrivateStorage(sourceFile: File, fileName: String): SavedAudioResult {
        val dest = File(privateMusicDir(context), fileName)
        sourceFile.copyTo(dest, overwrite = true)
        return SavedAudioResult(
            localUri = Uri.fromFile(dest).toString(),
            localFilePath = dest.absolutePath
        )
    }

    private fun sanitizeFilename(raw: String): String {
        val cleaned = raw
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return cleaned.ifBlank { "marea_track_${System.currentTimeMillis()}" }.take(90)
    }
}

/** Carpeta privada de las descargas (no la ven otras apps ni el escáner de música). */
fun privateMusicDir(context: Context): File =
    File(context.applicationContext.filesDir, "music").apply {
        mkdirs()
        runCatching { File(this, ".nomedia").createNewFile() }
    }
