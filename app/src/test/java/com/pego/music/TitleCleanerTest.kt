package com.pego.music

import com.pego.music.data.metadata.TitleCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleCleanerTest {

    @Test
    fun `cleanTitle removes official video and lyrics suffixes`() {
        val raw = "Daft Punk - Instant Crush (Official Video) [4K]"
        assertEquals("Daft Punk - Instant Crush", TitleCleaner.cleanTitle(raw))
    }

    @Test
    fun `extractArtistAndTrack splits dash separated titles`() {
        val (artist, track) = TitleCleaner.extractArtistAndTrack(
            rawTitle = "The Weeknd - Blinding Lights (Official Audio)",
            rawUploader = "TheWeekndVEVO"
        )
        assertEquals("The Weeknd", artist)
        assertEquals("Blinding Lights", track)
    }

    @Test
    fun `cleanArtist strips Topic suffix`() {
        assertEquals("Bad Bunny", TitleCleaner.cleanArtist("Bad Bunny - Topic"))
    }

    @Test
    fun `isGoodMetadataMatch accepts matching track ignoring accents`() {
        assertTrue(
            TitleCleaner.isGoodMetadataMatch(
                expectedTitle = "Tití Me Preguntó",
                expectedArtist = "Bad Bunny",
                candidateTitle = "Titi Me Pregunto",
                candidateArtist = "Bad Bunny"
            )
        )
    }

    @Test
    fun `isGoodMetadataMatch rejects unrelated iTunes match`() {
        assertFalse(
            TitleCleaner.isGoodMetadataMatch(
                expectedTitle = "Bohemian Rhapsody",
                expectedArtist = "Queen",
                candidateTitle = "Random Song Completely Different",
                candidateArtist = "Other Artist"
            )
        )
    }

    @Test
    fun `isLikelyOriginalTrack filters covers and remixes unless searched`() {
        assertFalse(
            TitleCleaner.isLikelyOriginalTrack(
                rawTitle = "Blinding Lights (Acoustic Cover)",
                rawArtist = "Some Cover Channel",
                userQuery = "The Weeknd Blinding Lights"
            )
        )
        assertTrue(
            TitleCleaner.isLikelyOriginalTrack(
                rawTitle = "Blinding Lights (Remix)",
                rawArtist = "DJ Test",
                userQuery = "Blinding Lights Remix"
            )
        )
        assertTrue(
            TitleCleaner.isLikelyOriginalTrack(
                rawTitle = "Blinding Lights",
                rawArtist = "The Weeknd",
                userQuery = "The Weeknd Blinding Lights"
            )
        )
    }

    @Test
    fun `isExplicitArtistMatch matches exact artist and explicit collaborations only`() {
        assertTrue(
            TitleCleaner.isExplicitArtistMatch(
                targetArtist = "Feid",
                candidateArtist = "Feid",
                candidateTitle = "Normal"
            )
        )
        assertTrue(
            TitleCleaner.isExplicitArtistMatch(
                targetArtist = "Feid",
                candidateArtist = "Karol G",
                candidateTitle = "FRIENDS (feat. Feid)"
            )
        )
        assertFalse(
            TitleCleaner.isExplicitArtistMatch(
                targetArtist = "Feid",
                candidateArtist = "J Balvin",
                candidateTitle = "Mi Gente"
            )
        )
        assertFalse(
            TitleCleaner.isExplicitArtistMatch(
                targetArtist = "Feid",
                candidateArtist = "Feidman",
                candidateTitle = "Other Song"
            )
        )
    }
}
