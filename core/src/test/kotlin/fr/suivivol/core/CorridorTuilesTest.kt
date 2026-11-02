package fr.suivivol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorridorTuilesTest {
    @Test
    fun `conversion lat lon vers tuile`() {
        // Au zoom 0, une seule tuile
        assertEquals(0, CorridorTuiles.tuileX(2.35, 0))
        assertEquals(0, CorridorTuiles.tuileY(48.85, 0))
        // Paris au zoom 8 : x = 129, y = 88 (valeurs classiques)
        assertEquals(129, CorridorTuiles.tuileX(2.35, 8))
        assertEquals(88, CorridorTuiles.tuileY(48.85, 8))
        // Longitude 180 se replie sur 0
        assertEquals(0, CorridorTuiles.tuileX(180.0, 3))
    }

    @Test
    fun `corridor LHR JFK reste raisonnable`() {
        val lhr = Coordonnee(51.470748, -0.459909)
        val jfk = Coordonnee(40.639447, -73.779317)
        val tuiles = CorridorTuiles.tuiles(lhr, jfk)
        assertTrue("nb tuiles = ${tuiles.size}", tuiles.size in 100..3000)
        assertTrue(tuiles.all { it.z in 3..8 })
        assertEquals(tuiles.size, tuiles.toSet().size)
        // les deux extrémités sont couvertes au zoom max
        assertTrue(tuiles.contains(Tuile(8, CorridorTuiles.tuileX(lhr.lon, 8), CorridorTuiles.tuileY(lhr.lat, 8))))
        assertTrue(tuiles.contains(Tuile(8, CorridorTuiles.tuileX(jfk.lon, 8), CorridorTuiles.tuileY(jfk.lat, 8))))
    }

    @Test
    fun `corridor traversant l antimeridien`() {
        val tokyo = Coordonnee(35.55, 139.78)
        val la = Coordonnee(33.94, -118.41)
        val tuiles = CorridorTuiles.tuiles(tokyo, la, zoomMin = 3, zoomMax = 5)
        assertTrue(tuiles.isNotEmpty())
        assertTrue(tuiles.all { it.x >= 0 && it.x < (1 shl it.z) })
    }
}
