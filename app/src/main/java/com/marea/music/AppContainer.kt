package com.marea.music

import android.content.Context
import com.marea.music.core.network.OkHttpDownloader
import com.marea.music.data.access.AccessMonitor
import com.marea.music.data.access.AccessRepository
import com.marea.music.data.history.ListeningHistory
import com.marea.music.data.download.DownloadOrchestrator
import com.marea.music.data.extractor.YouTubeExtractorRepository
import com.marea.music.data.local.MareaDatabase
import com.marea.music.data.metadata.LyricsRepository
import com.marea.music.data.metadata.MetadataEnricher
import com.marea.music.data.recommend.RecommendationEngine
import com.marea.music.data.tagger.AudioTagger
import com.marea.music.player.PlayerController
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val httpClient: OkHttpClient = OkHttpDownloader.getInstance().client
    val database: MareaDatabase = MareaDatabase.getInstance(appContext)
    val extractorRepo = YouTubeExtractorRepository()
    val metadataEnricher = MetadataEnricher(httpClient)
    val lyricsRepository = LyricsRepository(httpClient)
    val recommendationEngine = RecommendationEngine(httpClient, extractorRepo)
    val audioTagger = AudioTagger(appContext)
    val downloadOrchestrator = DownloadOrchestrator(appContext, database.mareaDao())
    val listeningHistory = ListeningHistory(appContext)
    val playerController = PlayerController(appContext, extractorRepo, listeningHistory)
    val accessRepository = AccessRepository(appContext, httpClient, BuildConfig.ACCESS_SERVER_URL)
    val accessMonitor = AccessMonitor(accessRepository, playerController).also { it.start() }
}
