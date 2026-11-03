package com.trucdecomptable.suivivol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la géométrie sphérique avec des valeurs de référence connues
 * (haversine Paris→Marseille, cap LHR→JFK, symétrie de l'écart de cap, etc.).
 */
class GeoTest {

    // Coordonnées de référence (WGS84).
    private val paris = 48.8566 to 2.3522
    private val marseille = 43.2965 to 5.3698
    private val lhr = 51.4775 to -0.461389
    private val jfk = 40.639447 to -73.779317

    @Test
    fun distanceParisMarseilleEnviron660Km() {
        // Valeur de référence connue : Paris → Marseille ≈ 660 km.
        val d = Geo.distanceKm(paris.first, paris.second, marseille.first, marseille.second)
        assertEquals(660.48, d, 1.0)
    }

    @Test
    fun capParisMarseilleVersLeSudSudEst() {
        // Paris → Marseille part vers le sud-sud-est (≈ 158°).
        val cap = Geo.capDeg(paris.first, paris.second, marseille.first, marseille.second)
        assertEquals(158.27, cap, 2.0)
    }

    @Test
    fun distanceLhrJfkMatcheLeCahierDesCharges() {
        // Référence du SPEC §7 : LHR → JFK = 5 539 km, cap initial 288°.
        val d = Geo.distanceKm(lhr.first, lhr.second, jfk.first, jfk.second)
        assertEquals(5539.5, d, 2.0)
        val cap = Geo.capDeg(lhr.first, lhr.second, jfk.first, jfk.second)
        assertEquals(287.93, cap, 1.0)
    }

    @Test
    fun ecartDeCapEstSymetriqueEtDans0a180() {
        // Le passage par 0° doit être bien géré : 350° et 10° sont écartés de 20°.
        assertEquals(20.0, Geo.ecartCapDeg(350.0, 10.0), 1e-9)
        assertEquals(20.0, Geo.ecartCapDeg(10.0, 350.0), 1e-9)
        // Caps opposés : écart maximal de 180°.
        assertEquals(180.0, Geo.ecartCapDeg(0.0, 180.0), 1e-9)
        assertEquals(180.0, Geo.ecartCapDeg(90.0, 270.0), 1e-9)
        // Caps identiques : écart nul.
        assertEquals(0.0, Geo.ecartCapDeg(45.0, 45.0), 1e-9)
    }

    @Test
    fun pointApresVersLEstFaitUneLongueurDeDegre() {
        // 111,195 km vers l'est depuis l'équateur ≈ 1° de longitude.
        val p = Geo.pointApres(0.0, 0.0, 90.0, 111.195)
        assertEquals(0.0, p.first, 1e-9)
        assertEquals(1.0, p.second, 1e-3)
    }

    @Test
    fun pointApresEstCoherentEnAllerRetour() {
        // Aller-retour : la distance et le cap sont conservés.
        val p = Geo.pointApres(10.0, 20.0, 45.0, 500.0)
        val d = Geo.distanceKm(10.0, 20.0, p.first, p.second)
        assertEquals(500.0, d, 0.01)
        val cap = Geo.capDeg(10.0, 20.0, p.first, p.second)
        assertEquals(45.0, cap, 0.1)
    }

    @Test
    fun pointLePlusProcheSurLaRouteRedonneLaCible() {
        // Cible pile sur la route (à l'est) : le point le plus proche est la cible.
        val p = Geo.pointLePlusProche(0.0, 0.0, 90.0, 0.0, 1.0)
        assertEquals(0.0, p.first, 1e-9)
        assertEquals(1.0, p.second, 1e-3)
        assertEquals(0.0, Geo.distanceKm(p.first, p.second, 0.0, 1.0), 1e-6)
    }

    @Test
    fun pointLePlusProcheHorsRouteDonneLaPerpendiculaire() {
        // Cible à 1° au nord de la route : le point le plus proche est sur
        // l'équateur, à la perpendiculaire (≈ 111 km de la cible).
        val p = Geo.pointLePlusProche(0.0, 0.0, 90.0, 1.0, 1.0)
        assertEquals(0.0, p.first, 1e-9)
        val ecart = Geo.distanceKm(p.first, p.second, 1.0, 1.0)
        assertTrue("écart transversal ≈ 111 km, obtenu $ecart", ecart in 110.0..112.0)
    }

    @Test
    fun pointLePlusProcheDerriereRetourneLePointDeDepart() {
        // Cible derrière le cap : le point le plus proche de la demi-route est l'origine.
        val p = Geo.pointLePlusProche(0.0, 0.0, 90.0, 0.0, -1.0)
        assertEquals(0.0, p.first, 1e-9)
        assertEquals(0.0, p.second, 1e-9)
    }
}
