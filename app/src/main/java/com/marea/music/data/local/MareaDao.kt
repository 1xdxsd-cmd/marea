package com.marea.music.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MareaDao {

    @Query("SELECT * FROM tracks ORDER BY updatedAt DESC")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE downloadStatus = 'COMPLETED' ORDER BY updatedAt DESC")
    fun observeDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE downloadStatus IN ('QUEUED', 'DOWNLOADING', 'TAGGING', 'FAILED') ORDER BY updatedAt DESC")
    fun observeActiveAndFailedDownloads(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackIgnore(track: TrackEntity)

    @Query("UPDATE tracks SET downloadStatus = :status, downloadProgress = :progress, updatedAt = :updatedAt WHERE id = :trackId")
    suspend fun updateDownloadState(
        trackId: String,
        status: String,
        progress: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE tracks SET
            cleanTitle = :cleanTitle,
            artist = :artist,
            album = :album,
            year = :year,
            hdCoverUrl = :hdCoverUrl,
            localFilePath = :localFilePath,
            localUri = :localUri,
            downloadStatus = :status,
            downloadProgress = 100,
            updatedAt = :updatedAt
        WHERE id = :trackId
        """
    )
    suspend fun markDownloadCompleted(
        trackId: String,
        cleanTitle: String,
        artist: String,
        album: String,
        year: String,
        hdCoverUrl: String,
        localFilePath: String?,
        localUri: String?,
        status: String = "COMPLETED",
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE tracks SET hdCoverUrl = :hdCoverUrl, localUri = :localUri, localFilePath = :localFilePath, updatedAt = :updatedAt WHERE id = :trackId")
    suspend fun updateTrackCover(
        trackId: String,
        hdCoverUrl: String,
        localUri: String?,
        localFilePath: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE tracks SET durationSeconds = :durationSeconds, localUri = :localUri, localFilePath = :localFilePath, updatedAt = :updatedAt WHERE id = :trackId")
    suspend fun updateTrackTrimmed(
        trackId: String,
        durationSeconds: Long,
        localUri: String?,
        localFilePath: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE tracks SET localUri = :localUri, localFilePath = :localFilePath WHERE id = :trackId")
    suspend fun updateTrackLocation(trackId: String, localUri: String, localFilePath: String)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun removeTrackFromAllPlaylists(trackId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("UPDATE playlists SET coverUrl = :coverUrl WHERE id = :playlistId AND coverUrl = ''")
    suspend fun updatePlaylistCoverIfEmpty(playlistId: Long, coverUrl: String)

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylistsWithTracks(): Flow<List<PlaylistWithTracks>>

    @Query("SELECT * FROM playlists WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getPlaylistBySourceUrl(sourceUrl: String): PlaylistEntity?

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistRefs(playlistId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)
}
