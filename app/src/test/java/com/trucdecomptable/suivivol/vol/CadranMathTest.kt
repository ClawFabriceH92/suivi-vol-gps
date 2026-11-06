package com.trucdecomptable.suivivol.vol

import org.junit.Assert.assertEquals
import org.junit.Test

/** Géométrie et libellés des cadrans du tableau de bord. */
class CadranMathTest {

    @Test
    fun `cap normalise ramene dans l intervalle 0 360`() {
        assertEquals(10.0, CadranMath.capNormalise(370.0), 1e-9)
        assertEquals(350.0, CadranMath.capNormalise(-10.0), 1e-9)
        assertEquals(0.0, CadranMath.capNormalise(360.0), 1e-9)
        assertEquals(180.0, CadranMath.capNormalise(180.0), 1e-9)
    }

    @Test
    fun `angle d aiguille suit les bornes de l arc`() {
        // Arc symétrique -120 .. +120 : le milieu de plage tombe à 0 (haut du cadran).
        assertEquals(-120.0, CadranMath.angleAiguille(-10.0, -10.0, 10.0), 1e-9)
        assertEquals(120.0, CadranMath.angleAiguille(10.0, -10.0, 10.0), 1e-9)
        assertEquals(0.0, CadranMath.angleAiguille(0.0, -10.0, 10.0), 1e-9)
        // Vitesse : 150 km/h au milieu d'un cadran 0..300.
        assertEquals(0.0, CadranMath.angleAiguille(150.0, 0.0, 300.0), 1e-9)
    }

    @Test
    fun `angle d aiguille ecrete les valeurs hors bornes`() {
        assertEquals(-120.0, CadranMath.angleAiguille(-50.0, -10.0, 10.0), 1e-9)
        assertEquals(120.0, CadranMath.angleAiguille(9999.0, -10.0, 10.0), 1e-9)
    }

    @Test
    fun `vitesse verticale bornee et libelle avec signe`() {
        assertEquals(10.0, CadranMath.vitesseVerticaleBornee(25.0), 1e-9)
        assertEquals(-10.0, CadranMath.vitesseVerticaleBornee(-25.0), 1e-9)
        assertEquals("+2 m/s", CadranMath.libelleVitesseVerticale(2.4))
        assertEquals("-3 m/s", CadranMath.libelleVitesseVerticale(-3.2))
        assertEquals("0 m/s", CadranMath.libelleVitesseVerticale(0.0))
        assertEquals("—", CadranMath.libelleVitesseVerticale(null))
    }

    @Test
    fun `valeurs inutilisables affichees en tiret`() {
        assertEquals("—", CadranMath.libelleCap(null))
        assertEquals("—", CadranMath.libelleAltitude(null))
        assertEquals("—", CadranMath.libelleVitesse(null))
        assertEquals("—", CadranMath.libelleCap(Double.NaN))
    }

    @Test
    fun `cap affiche sur trois chiffres comme en aeronautique`() {
        assertEquals("005°", CadranMath.libelleCap(4.6))
        assertEquals("000°", CadranMath.libelleCap(360.0))
        assertEquals("270°", CadranMath.libelleCap(270.0))
    }

    @Test
    fun `points cardinaux par secteurs de 45 degres`() {
        assertEquals("N", CadranMath.pointCardinal(0.0))
        assertEquals("NE", CadranMath.pointCardinal(45.0))
        assertEquals("E", CadranMath.pointCardinal(90.0))
        assertEquals("S", CadranMath.pointCardinal(180.0))
        assertEquals("O", CadranMath.pointCardinal(270.0))
        assertEquals("N", CadranMath.pointCardinal(350.0))
    }
}
