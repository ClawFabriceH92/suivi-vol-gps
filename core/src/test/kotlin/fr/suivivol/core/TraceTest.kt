package fr.suivivol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceTest {
    private fun p(tSec: Long, lat: Double, lon: Double, v: Double? = null, alt: Double? = null) =
        PointTrace(tSec * 1000, lat, lon, altitudeGpsM = alt, vitesseSolKmh = v)

    @Test
    fun `distance parcourue somme les segments`() {
        // 4 points vers le nord, 0,01° ≈ 1,112 km chacun, 1 s entre chaque point : impossible
        // physiquement (4000 km/h) -> on espace de 10 s (400 km/h).
        val pts = (0..3).map { p(it * 10L, 48.0 + it * 0.01, 2.0) }
        assertEquals(3 * 1.1119, AnalyseTrace.distanceParcourueKm(pts), 0.01)
    }

    @Test
    fun `segment de plus de 10 minutes ignore`() {
        val pts = listOf(p(0, 48.0, 2.0), p(601, 48.01, 2.0), p(611, 48.02, 2.0))
        assertEquals(1.1119, AnalyseTrace.distanceParcourueKm(pts), 0.01)
    }

    @Test
    fun `saut impossible ignore`() {
        // 111 km en 1 s = 400 000 km/h
        val pts = listOf(p(0, 48.0, 2.0), p(1, 49.0, 2.0), p(11, 49.01, 2.0))
        assertEquals(1.1119, AnalyseTrace.distanceParcourueKm(pts), 0.01)
    }

    @Test
    fun `croisiere a 900 km h toutes les 15 s est conservee`() {
        // 900 km/h × 15 s = 3,75 km par segment, soit ≈ 0,0337° de latitude
        val pts = (0..4).map { p(it * 15L, 48.0 + it * 0.0337, 2.0) }
        assertTrue(AnalyseTrace.distanceParcourueKm(pts) > 14.9)
    }

    @Test
    fun `statistiques`() {
        val pts = listOf(
            // 0,5° de latitude (≈ 55,6 km) toutes les 5 minutes : 667 km/h
            p(0, 48.0, 2.0, v = 100.0, alt = 100.0),
            p(300, 48.5, 2.0, v = 300.0, alt = 10_000.0),
            p(600, 49.0, 2.0, v = 200.0, alt = 5_000.0),
        )
        val s = AnalyseTrace.statistiques(pts, Coordonnee(48.0, 2.0), Coordonnee(49.0, 2.0))
        assertEquals(3, s.nbPoints)
        assertEquals(10_000.0, s.altitudeMaxM!!, 1e-9)
        assertEquals(300.0, s.vitesseMaxKmh!!, 1e-9)
        assertEquals(111.2, s.distanceParcourueKm, 0.5)
        assertEquals(667.1, s.vitesseMoyenneKmh!!, 1.0)
        assertNotNull(s.ecartMaxRouteKm)
        assertEquals(0.0, s.ecartMaxRouteKm!!, 0.01)
    }
}
