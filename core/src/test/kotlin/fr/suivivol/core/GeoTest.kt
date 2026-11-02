package fr.suivivol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    private val lhr = Coordonnee(51.470748, -0.459909)
    private val jfk = Coordonnee(40.639447, -73.779317)
    private val cdg = Coordonnee(49.00896, 2.554117)

    @Test
    fun `distance LHR JFK vaut environ 5539 km`() {
        assertEquals(5539.8, Geo.distanceKm(lhr, jfk), 1.0)
    }

    @Test
    fun `distance CDG JFK vaut environ 5834 km`() {
        assertEquals(5834.1, Geo.distanceKm(cdg, jfk), 1.0)
    }

    @Test
    fun `distance nulle entre un point et lui-meme`() {
        assertEquals(0.0, Geo.distanceKm(lhr, lhr), 1e-9)
    }

    @Test
    fun `cap initial LHR JFK vaut 288 degres`() {
        assertEquals(288.0, Geo.capInitialDeg(lhr, jfk), 0.5)
    }

    @Test
    fun `cap initial plein nord et plein est`() {
        assertEquals(0.0, Geo.capInitialDeg(0.0, 0.0, 10.0, 0.0), 1e-6)
        assertEquals(90.0, Geo.capInitialDeg(0.0, 0.0, 0.0, 10.0), 1e-6)
    }

    @Test
    fun `normalisation du cap dans 0 360`() {
        assertEquals(350.0, Geo.normaliserCap(-10.0), 1e-9)
        assertEquals(10.0, Geo.normaliserCap(370.0), 1e-9)
        assertEquals(0.0, Geo.normaliserCap(360.0), 1e-9)
    }

    @Test
    fun `rose des vents`() {
        assertEquals("N", Geo.roseDesVents(0.0))
        assertEquals("N", Geo.roseDesVents(359.0))
        assertEquals("E", Geo.roseDesVents(90.0))
        assertEquals("O", Geo.roseDesVents(274.0))
        assertEquals("SO", Geo.roseDesVents(225.0))
        assertEquals("274° O", Geo.formatCap(274.0))
    }

    @Test
    fun `point intermediaire a mi-chemin est equidistant`() {
        val milieu = Geo.pointIntermediaire(lhr, jfk, 0.5)
        val d1 = Geo.distanceKm(lhr, milieu)
        val d2 = Geo.distanceKm(milieu, jfk)
        assertEquals(d1, d2, 0.5)
        assertEquals(Geo.distanceKm(lhr, jfk), d1 + d2, 0.5)
        assertEquals(lhr, Geo.pointIntermediaire(lhr, jfk, 0.0))
    }

    @Test
    fun `points grand cercle contiennent les extremites`() {
        val pts = Geo.pointsGrandCercle(lhr, jfk, 10)
        assertEquals(11, pts.size)
        assertEquals(lhr.lat, pts.first().lat, 1e-6)
        assertEquals(jfk.lat, pts.last().lat, 1e-6)
        assertEquals(jfk.lon, pts.last().lon, 1e-6)
    }

    @Test
    fun `ecart a la route nul sur la route et positif a cote`() {
        val surRoute = Geo.pointIntermediaire(lhr, jfk, 0.3)
        assertEquals(0.0, Geo.ecartRouteKm(surRoute, lhr, jfk), 0.01)
        val aCote = Coordonnee(surRoute.lat + 1.0, surRoute.lon)
        val ecart = Geo.ecartRouteKm(aCote, lhr, jfk)
        assertTrue("écart $ecart", ecart in 80.0..115.0)
    }
}
