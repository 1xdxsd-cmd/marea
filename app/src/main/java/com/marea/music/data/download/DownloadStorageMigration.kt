package com.marea.music.data.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import com.marea.music.data.local.MareaDao
import com.marea.music.data.tagger.privateMusicDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Las descargas antes se guardaban en Música/Marea y salían en otros reproductores.
 * Al abrir la app se mueven a la carpeta privada y se borra la copia pública.
 */
class DownloadStorageMigration(
    private val context: Context,
    private val dao: MareaDao
) {
    suspend fun run() = withContext(Dispatchers.IO) {
        val dir = privateMusicDir(context)
        val tracks = runCatching { dao.observeDownloadedTracks().first() }.getOrDefault(emptyList())
        for (t in tracks) {
            val path = t.localFilePath
            if (path != null && path.startsWith(dir.absolutePath)) continue
            val uri = t.localUri.orEmpty()
            val dest = File(dir, t.id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".m4a")
            val copied = runCatching {
                when {
                    uri.startsWith("content://") ->
                        context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                            true
                        } ?: false
                    path != null && File(path).exists() -> {
                        File(path).copyTo(dest, overwrite = true); true
                    }
                    uri.startsWith("file:") -> {
                        val f = Uri.parse(uri).path?.let(::File)
                        if (f != null && f.exists()) { f.copyTo(dest, overwrite = true); true } else false
                    }
                    else -> false
                }
            }.getOrDefault(false)
            if (!copied || dest.length() == 0L) {
                dest.delete()
                continue
            }
            dao.updateTrackLocation(t.id, Uri.fromFile(dest).toString(), dest.absolutePath)
            // Quitar la copia pública (si Android lo permite; si no, se queda donde estaba).
            runCatching {
                when {
                    uri.startsWith("content://") -> context.contentResolver.delete(Uri.parse(uri), null, null)
                    path != null -> {
                        File(path).delete()
                        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                    }
                    else -> Unit
                }
            }
        }
    }
}
