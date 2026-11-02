package fr.suivivol.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitesTest {
    @Test
    fun `altitude en metres et pieds`() {
        assertEquals("10 680 m", Unites.formatAltitude(10_680.0, UniteAltitude.METRES))
        assertEquals("35 039 ft", Unites.formatAltitude(10_680.0, UniteAltitude.PIEDS))
        assertEquals("—", Unites.formatAltitude(null, UniteAltitude.METRES))
    }

    @Test
    fun `vitesse en km h et noeuds`() {
        assertEquals("890 km/h", Unites.formatVitesse(890.0, UniteVitesse.KMH))
        assertEquals("481 kt", Unites.formatVitesse(890.0, UniteVitesse.NOEUDS))
    }

    @Test
    fun `distance`() {
        assertEquals("5 540 km", Unites.formatDistanceKm(5539.8))
        assertEquals("3,2 km", Unites.formatDistanceKm(3.21))
    }
}
