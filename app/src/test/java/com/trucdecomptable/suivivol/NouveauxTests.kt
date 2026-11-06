package com.trucdecomptable.suivivol

import com.trucdecomptable.suivivol.data.SourceTuiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy

/**
 * Verrouille la cause du plantage Android 14 : la source de tuiles doit
 * accepter le téléchargement en masse, sinon CacheManager lève
 * TileSourcePolicyException depuis une AsyncTask et ferme l'application.
 */
class SourceTuilesTest {

    @Test
    fun `la source OSM de l application accepte le telechargement en masse`() {
        val source = SourceTuiles.OSM as OnlineTileSourceBase
        assertTrue(
            "SourceTuiles.OSM doit accepter le téléchargement en masse",
            source.tileSourcePolicy.acceptsBulkDownload(),
        )
    }

    @Test
    fun `la source MAPNIK d osmdroid le refuse`() {
        // Preuve du diagnostic : c'est bien cette source qui provoquait le plantage.
        val mapnik = TileSourceFactory.MAPNIK as OnlineTileSourceBase
        assertFalse(mapnik.tileSourcePolicy.acceptsBulkDownload())
    }

    @Test
    fun `construction de la politique sans drapeau interdit`() {
        assertTrue(TileSourcePolicy(2, 0).acceptsBulkDownload())
        assertEquals(2, TileSourcePolicy(2, 0).maxConcurrent)
        assertFalse(TileSourcePolicy(2, TileSourcePolicy.FLAG_NO_BULK).acceptsBulkDownload())
    }
}
