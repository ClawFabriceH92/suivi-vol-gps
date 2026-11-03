package com.trucdecomptable.suivivol.data

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Géométrie sphérique (WGS84) pure, sans aucune dépendance Android : toutes les
 * fonctions sont testables en JUnit avec des valeurs de référence connues.
 *
 * Le rayon terrestre est celui du SPEC (§7) : 6 371 008,8 m.
 */
object Geo {

    const val RAYON_TERRE_KM = 6371.0088

    /**
     * Distance grand-cercle (formule de haversine) entre deux points, en km.
     */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return 2 * RAYON_TERRE_KM * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Cap initial (relèvement orthodromique) de `(lat1, lon1)` vers
     * `(lat2, lon2)`, ramené dans [0, 360[.
     */
    fun capDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Écart angulaire entre deux caps, dans [0, 180] : 350° et 10° sont écartés
     * de 20° (le passage par 0° est bien géré).
     */
    fun ecartCapDeg(cap1: Double, cap2: Double): Double {
        val diff = ((cap1 - cap2) % 360.0 + 360.0) % 360.0
        return if (diff <= 180.0) diff else 360.0 - diff
    }

    /**
     * Point atteint en partant de `(lat, lon)` dans la direction `capDeg` sur
     * `distanceKm` kilomètres (problème direct de géodésie sphérique).
     */
    fun pointApres(lat: Double, lon: Double, capDeg: Double, distanceKm: Double): Pair<Double, Double> {
        val phi1 = Math.toRadians(lat)
        val lambda1 = Math.toRadians(lon)
        val delta = distanceKm / RAYON_TERRE_KM
        val theta = Math.toRadians(capDeg)
        val phi2 = asin(sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta))
        val lambda2 = lambda1 + atan2(sin(theta) * sin(delta) * cos(phi1), cos(delta) - sin(phi1) * sin(phi2))
        val lon2 = (Math.toDegrees(lambda2) + 540.0) % 360.0 - 180.0
        return Math.toDegrees(phi2) to lon2
    }

    /**
     * Point de la demi-route (grand cercle issu de `(lat, lon)` dans la
     * direction `capDeg`) le plus proche de la cible `(latCible, lonCible)`.
     *
     * Utile pour mesurer l'écart transversal d'un aéroport par rapport à la
     * route suivie. Si la cible est « derrière » le cap, le point le plus proche
     * sur la demi-droite est le point de départ lui-même.
     */
    fun pointLePlusProche(lat: Double, lon: Double, capDeg: Double, latCible: Double, lonCible: Double): Pair<Double, Double> {
        val distanceAngulaire = distanceKm(lat, lon, latCible, lonCible) / RAYON_TERRE_KM
        if (distanceAngulaire < 1e-12) return lat to lon

        val capCible = capDeg(lat, lon, latCible, lonCible)
        val ecart = ((capCible - capDeg) % 360.0 + 360.0) % 360.0

        // Cible à plus de 90° du cap : le point le plus proche sur la demi-route
        // est le départ (la route part dans l'autre sens).
        if (ecart > 90.0 && ecart < 270.0) return lat to lon

        val dXtrack = asin(sin(distanceAngulaire) * sin(Math.toRadians(ecart)))
        val denom = cos(dXtrack)
        val dAlong = if (abs(denom) < 1e-12) distanceAngulaire else acos(cos(distanceAngulaire) / denom)
        return pointApres(lat, lon, capDeg, dAlong * RAYON_TERRE_KM)
    }
}
