package com.trucdecomptable.suivivol.vol

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la construction du journal GPX : en-tête valide, un `trkpt` par
 * point, nom de fichier daté et horodatage ISO 8601 UTC.
 */
class JournalGpxTest {

    private fun point(ms: Long, lat: Double, lon: Double, altitude: Double) =
        PointVol(lat, lon, altitude, 800.0, 270.0, 0.0, ms)

    @Test
    fun enTeteGpxValide() {
        val gpx = JournalGpx.construire(emptyList())
        assertTrue(gpx.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(gpx.contains("<gpx version=\"1.1\" creator=\"${JournalGpx.CREATEUR}\""))
        assertTrue(gpx.contains("xmlns=\"http://www.topografix.com/GPX/1/1\""))
    }

    @Test
    fun unTrkptParPoint() {
        val points = listOf(
            point(1_700_000_000_000L, 49.0, 2.55, 100.0),
            point(1_700_000_002_000L, 45.0, -30.0, 11_000.0),
            point(1_700_000_004_000L, 40.6, -73.8, 5.0),
        )
        val gpx = JournalGpx.construire(points)
        assertEquals(3, Regex("<trkpt ").findAll(gpx).count())
        assertTrue(gpx.contains("<trk>"))
        assertTrue(gpx.contains("<trkseg>"))
    }

    @Test
    fun aucunTrkptPourUneListeVide() {
        val gpx = JournalGpx.construire(emptyList())
        assertEquals(0, Regex("<trkpt ").findAll(gpx).count())
    }

    @Test
    fun nomFichierDate() {
        assertEquals("suivi-vol-2026-11-03.gpx", JournalGpx.nomFichier(LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun horodatageIso8601Utc() {
        val ms = 1_700_000_004_567L
        val gpx = JournalGpx.construire(listOf(point(ms, 40.6, -73.8, 5.0)))
        val attendu = Instant.ofEpochMilli(ms).toString()
        // Le time doit être exactement l'instant epoch formaté en ISO 8601 UTC.
        assertTrue("time attendu <time>$attendu</time> absent de :\n$gpx",
            gpx.contains("<time>$attendu</time>"))
        // ISO 8601 UTC : la chaîne se termine par « Z » (pas de décalage horaire).
        assertTrue(attendu.endsWith("Z"))
    }

    @Test
    fun altitudeEnMetresDansEle() {
        val gpx = JournalGpx.construire(listOf(point(0L, 0.0, 0.0, 1234.5)))
        assertTrue(gpx.contains("<ele>1234.5</ele>"))
    }
}
