package com.trucdecomptable.suivivol.vol

import com.trucdecomptable.suivivol.data.Aeroport
import com.trucdecomptable.suivivol.data.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie le moteur de devinette avec un scénario CDG → JFK simulé par des
 * points GPS synthétiques (liste d'aéroports réduite et déterministe, pour ne
 * pas dépendre du fichier de 8 925 lignes).
 */
class DevinetteVolTest {

    // Aéroports synthétiques, coordonnées réelles (WGS84).
    private val cdg = a("CDG", 49.00896, 2.554117)
    private val ory = a("ORY", 48.723333, 2.379444)
    private val jfk = a("JFK", 40.639447, -73.779317)
    private val ewr = a("EWR", 40.6925, -74.168667)
    private val lhr = a("LHR", 51.4775, -0.461389)
    private val bos = a("BOS", 42.3656, -71.0096)
    private val yyz = a("YYZ", 43.6772, -79.6306)
    private val tous = listOf(cdg, ory, jfk, ewr, lhr, bos, yyz)

    private fun a(code: String, lat: Double, lon: Double) =
        Aeroport(code, code, "", code, lat, lon, "", "", true)

    /**
     * Fait rouler l'avion au sol à CDG puis décoller (vitesse > 150 km/h et
     * altitude > 1000 m tenues 60 s). Renvoie l'état après décollage.
     */
    private fun decoller(dev: DevinetteVol): EtatVol {
        var etat = EtatVol(PhaseVol.SOL, null, null, emptyList(), 0.0)
        var t = 0L
        // Roulage au sol : vitesse nulle, altitude au niveau du terrain.
        for (i in 0 until 3) {
            etat = dev.ajouter(PointVol(cdg.lat, cdg.lon, 100.0, 0.0, 0.0, 0.0, t))
            t += 10_000L
        }
        // Montée : conditions de décollage tenues 60 s (7 points espacés de 10 s).
        for (i in 0 until 7) {
            etat = dev.ajouter(PointVol(cdg.lat, cdg.lon, 1100.0 + i * 100.0, 200.0, 291.6, 5.0, t))
            t += 10_000L
        }
        return etat
    }

    @Test
    fun auSolSansCandidat() {
        val dev = DevinetteVol(tous)
        val etat = dev.ajouter(PointVol(cdg.lat, cdg.lon, 100.0, 0.0, 0.0, 0.0, 0L))
        assertEquals(PhaseVol.SOL, etat.phase)
        assertTrue(etat.candidats.isEmpty())
        assertNull(etat.arrivee)
        assertEquals(0.0, etat.confiance, 0.0)
        // Le départ reste mémorisé (aéroport le plus proche au sol).
        assertEquals("CDG", etat.depart?.code)
    }

    @Test
    fun decollagePresDeCdgMemoriseLeDepart() {
        val dev = DevinetteVol(tous)
        val etat = decoller(dev)
        assertTrue("phase en vol, obtenue ${etat.phase}", etat.phase != PhaseVol.SOL)
        assertEquals("CDG", etat.depart?.code)
    }

    @Test
    fun croisiereCapOuestFiltreLesCandidats() {
        val dev = DevinetteVol(tous)
        decoller(dev)

        // Croisière sur la route CDG → JFK, cap pointé vers JFK.
        val capVersJfk = Geo.capDeg(cdg.lat, cdg.lon, jfk.lat, jfk.lon)
        val p = Geo.pointApres(cdg.lat, cdg.lon, capVersJfk, 2500.0)
        val cap = Geo.capDeg(p.first, p.second, jfk.lat, jfk.lon)

        val etat = dev.ajouter(PointVol(p.first, p.second, 11_000.0, 900.0, cap, 0.0, 2_000_000L))

        assertEquals(PhaseVol.CROISIERE, etat.phase)
        // JFK est droit devant : il mène la liste des candidats.
        assertEquals("JFK", etat.arrivee?.code)
        val codes = etat.candidats.map { it.aeroport.code }
        // Les aéroports derrière nous sont écartés du cône de ±15°.
        assertTrue("CDG/ORY/LHR ne doivent pas être candidats, obtenu $codes",
            !codes.contains("CDG") && !codes.contains("ORY") && !codes.contains("LHR"))
        // Tous les candidats retenus respectent les règles : > 100 km et écart < 15°.
        for (c in etat.candidats) {
            val d = Geo.distanceKm(p.first, p.second, c.aeroport.lat, c.aeroport.lon)
            assertTrue("candidat à plus de 100 km, obtenu $d", d > 100.0)
            val ecart = Geo.ecartCapDeg(cap, Geo.capDeg(p.first, p.second, c.aeroport.lat, c.aeroport.lon))
            assertTrue("écart de cap < 15°, obtenu $ecart", ecart < 15.0)
        }
    }

    @Test
    fun descentePresDeJfkLeMetEnTete() {
        val dev = DevinetteVol(tous)
        decoller(dev)

        // Avion 150 km « avant » JFK, cap 110° : avec 3000 m d'altitude, une
        // pente de -5 m/s et 900 km/h, le point d'impact projeté (150 km devant)
        // tombe à quelques kilomètres de JFK.
        val capApproche = 110.0
        val position = Geo.pointApres(jfk.lat, jfk.lon, capApproche + 180.0, 150.0)

        val etat = dev.ajouter(
            PointVol(position.first, position.second, 3000.0, 900.0, capApproche, -5.0, 3_000_000L)
        )

        assertEquals(PhaseVol.DESCENTE, etat.phase)
        assertEquals("JFK", etat.arrivee?.code)
        assertEquals("JFK", etat.candidats.first().aeroport.code)
        assertTrue("confiance > 0,5, obtenue ${etat.confiance}", etat.confiance > 0.5)
    }
}
