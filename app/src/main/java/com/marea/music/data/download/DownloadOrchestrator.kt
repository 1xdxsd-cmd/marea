package com.marea.music.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.marea.music.data.local.MareaDao
import com.marea.music.data.local.PlaylistEntity
import com.marea.music.data.local.PlaylistTrackCrossRef
import com.marea.music.data.local.TrackEntity
import com.marea.music.domain.model.DownloadStatus
import com.marea.music.domain.model.PlaylistPreview
import com.marea.music.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadOrchestrator(
    private val context: Context,
    private val dao: MareaDao
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    suspend fun enqueueSingleTrack(track: TrackItem) = withContext(Dispatchers.IO) {
        val existing = dao.getTrackById(track.id)
        if (existing != null && existing.downloadStatus in ACTIVE_OR_DONE_STATUSES) {
            return@withContext
        }

        val queuedEntity = TrackEntity.fromDomain(
            track.copy(downloadStatus = DownloadStatus.QUEUED, downloadProgress = 0)
        )
        dao.upsertTrack(queuedEntity)
        enqueueWorkerForTrack(track)
    }

    suspend fun enqueuePlaylist(
        preview: PlaylistPreview,
        tracksToDownload: List<TrackItem> = preview.tracks
    ): Long = withContext(Dispatchers.IO) {
        if (tracksToDownload.isEmpty()) return@withContext -1L

        val existingPlaylist = dao.getPlaylistBySourceUrl(preview.sourceUrl)
        val playlistId = existingPlaylist?.id ?: dao.insertPlaylist(
            PlaylistEntity(
                name = preview.title,
                coverUrl = preview.thumbnailUrl,
                sourceUrl = preview.sourceUrl
            )
        )

        val tracksPending = mutableListOf<TrackItem>()
        for (track in tracksToDownload) {
            val existing = dao.getTrackById(track.id)
            if (existing == null || existing.downloadStatus !in ACTIVE_OR_DONE_STATUSES) {
                dao.upsertTrack(
                    TrackEntity.fromDomain(
                        track.copy(downloadStatus = DownloadStatus.QUEUED, downloadProgress = 0)
                    )
                )
                tracksPending.add(track)
            }
        }

        val refs = tracksToDownload.mapIndexed { index, track ->
            PlaylistTrackCrossRef(playlistId = playlistId, trackId = track.id, position = index)
        }
        dao.insertPlaylistRefs(refs)

        // Cada canción es una tarea independiente: si una falla, las demás siguen. El límite de
        // descargas simultáneas lo pone DownloadWorker.
        tracksPending.forEach { enqueueWorkerForTrack(it) }

        playlistId
    }

    suspend fun cancelOrRemoveTrack(trackId: String) = withContext(Dispatchers.IO) {
        workManager.cancelAllWorkByTag("download_$trackId")
        workManager.cancelUniqueWork("track_$trackId")
        val existing = dao.getTrackById(trackId)
        if (existing != null && existing.downloadStatus != DownloadStatus.COMPLETED.name) {
            dao.deleteTrack(trackId)
        }
    }

    private fun enqueueWorkerForTrack(track: TrackItem) {
        val request = buildWorkRequest(track)
        workManager.enqueueUniqueWork(
            "track_${track.id}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun buildWorkRequest(track: TrackItem) =
        OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_TRACK_ID, track.id)
                    .putString(DownloadWorker.KEY_TITLE, track.cleanTitle.ifBlank { track.title })
                    .putString(DownloadWorker.KEY_ARTIST, track.artist)
                    .putString(DownloadWorker.KEY_SOURCE_URL, track.sourceUrl)
                    .putString(DownloadWorker.KEY_THUMB_URL, track.bestCoverUrl)
                    .build()
            )
            .addTag("download_${track.id}")
            .build()

    companion object {
        private val ACTIVE_OR_DONE_STATUSES = setOf(
            DownloadStatus.COMPLETED.name,
            DownloadStatus.QUEUED.name,
            DownloadStatus.DOWNLOADING.name,
            DownloadStatus.TAGGING.name
        )
    }
}
