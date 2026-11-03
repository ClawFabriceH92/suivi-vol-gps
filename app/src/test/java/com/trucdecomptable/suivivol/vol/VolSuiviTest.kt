package com.trucdecomptable.suivivol.vol

import com.trucdecomptable.suivivol.data.Aeroport
import com.trucdecomptable.suivivol.data.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Vérifie les calculs d'avancement (distance, durée, progression, résumé) avec
 * des valeurs de référence et les cas dégénérés (distance nulle, vitesse
 * nulle, arrivée absente).
 */
class VolSuiviTest {

    private val cdg = Aeroport("CDG", "CDG", "LFPG", "Charles de Gaulle", 49.00896, 2.554117, "FR", "Paris", true)
    private val jfk = Aeroport("JFK", "JFK", "KJFK", "John F. Kennedy", 40.639447, -73.779317, "US", "New York", true)

    @Test
    fun distanceRestanteEgaleDistanceGrandCercle() {
        val lat = 45.0
        val lon = -30.0
        val attendu = Geo.distanceKm(lat, lon, jfk.lat, jfk.lon)
        assertEquals(attendu, VolSuivi.distanceRestanteKm(lat, lon, jfk), 1e-9)
    }

    @Test
    fun distanceRestanteNulleSurLAeroportDArrivee() {
        assertEquals(0.0, VolSuivi.distanceRestanteKm(jfk.lat, jfk.lon, jfk), 1e-6)
    }

    @Test
    fun dureeRestantePrefereLaVitesseMoyenne() {
        // 400 km à 800 km/h → 30 min ; la moyenne prime sur l'instantanée (900).
        assertEquals(30, VolSuivi.dureeRestanteMin(400.0, 900.0, 800.0))
    }

    @Test
    fun dureeRestanteSansVitesseMoyenneUtiliseLaVitesseSol() {
        assertEquals(30, VolSuivi.dureeRestanteMin(400.0, 800.0, null))
    }

    @Test
    fun dureeRestanteNulleQuandDistanceNulle() {
        // Déjà arrivé : 0, même sans vitesse exploitable.
        assertEquals(0, VolSuivi.dureeRestanteMin(0.0, 0.0, null))
    }

    @Test
    fun dureeRestanteNulleQuandAucuneVitesseExploitable() {
        assertNull(VolSuivi.dureeRestanteMin(400.0, 0.0, null))
        assertNull(VolSuivi.dureeRestanteMin(400.0, 0.0, 0.0))
    }

    @Test
    fun progressionNulleAuDepartEtTotaleALArrivee() {
        assertEquals(0.0, VolSuivi.progression(cdg, jfk, cdg.lat, cdg.lon), 1e-9)
        assertEquals(1.0, VolSuivi.progression(cdg, jfk, jfk.lat, jfk.lon), 1e-9)
    }

    @Test
    fun progressionAMiParcoursEnvironUnDemi() {
        val totale = Geo.distanceKm(cdg.lat, cdg.lon, jfk.lat, jfk.lon)
        val cap = Geo.capDeg(cdg.lat, cdg.lon, jfk.lat, jfk.lon)
        val milieu = Geo.pointApres(cdg.lat, cdg.lon, cap, totale / 2.0)
        assertEquals(0.5, VolSuivi.progression(cdg, jfk, milieu.first, milieu.second), 0.01)
    }

    @Test
    fun progressionSansDepartOuSansArriveeEstNulle() {
        assertEquals(0.0, VolSuivi.progression(null, jfk, 45.0, -30.0), 1e-9)
        assertEquals(0.0, VolSuivi.progression(cdg, null, 45.0, -30.0), 1e-9)
    }

    @Test
    fun progressionEstBorneeInferieurementAZero() {
        // Position à l'antipode de l'arrivée : distance restante > distance
        // totale, la progression brute serait négative → bornée à 0.
        assertEquals(0.0, VolSuivi.progression(cdg, jfk, -jfk.lat, jfk.lon + 180.0), 1e-9)
    }

    @Test
    fun resumeLisible() {
        assertEquals("412 km · 38 min · 640 km/h", VolSuivi.resume(412.0, 38, 640.0))
    }
}
