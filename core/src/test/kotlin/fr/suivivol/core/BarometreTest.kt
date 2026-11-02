package fr.suivivol.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BarometreTest {
    @Test
    fun `pression de reference donne zero metre`() {
        assertEquals(0.0, Barometre.altitudeM(1013.25), 1e-9)
    }

    @Test
    fun `226 hPa donnent environ 11000 m`() {
        assertEquals(11_001.7, Barometre.altitudeM(226.32), 1.0)
    }

    @Test
    fun `500 hPa donnent environ 5575 m`() {
        assertEquals(5_575.3, Barometre.altitudeM(500.0), 1.0)
    }

    @Test
    fun `recalage lisse sur 10 mesures`() {
        val r = RecalageBarometrique()
        assertEquals(0.0, r.decalageM, 1e-9)
        repeat(10) { r.ajouterMesure(1000.0 + 50, 1000.0) }
        assertEquals(50.0, r.decalageM, 1e-9)
        assertEquals(1050.0, r.altitudeAffichee(1000.0), 1e-9)
        // dix nouvelles mesures à +30 remplacent entièrement les anciennes
        repeat(10) { r.ajouterMesure(1030.0, 1000.0) }
        assertEquals(30.0, r.decalageM, 1e-9)
        assertEquals(10, r.nombreMesures)
    }

    @Test
    fun `recalage plafonne a 150 m`() {
        val r = RecalageBarometrique()
        r.ajouterMesure(2000.0, 1000.0)
        assertEquals(150.0, r.decalageM, 1e-9)
        r.reinitialiser()
        r.ajouterMesure(0.0, 1000.0)
        assertEquals(-150.0, r.decalageM, 1e-9)
    }
}
