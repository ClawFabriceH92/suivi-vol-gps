package fr.suivivol.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Un point de trace GPS enregistré pendant le vol. */
data class PointTrace(
    val horodatageMs: Long,
    val lat: Double,
    val lon: Double,
    val altitudeGpsM: Double? = null,
    val altitudeBaroM: Double? = null,
    val vitesseSolKmh: Double? = null,
    val capDeg: Double? = null,
    val precisionM: Double? = null,
) {
    val coordonnee: Coordonnee get() = Coordonnee(lat, lon)

    /** Altitude à afficher : barométrique recalée si disponible, sinon GPS. */
    val altitudeAfficheeM: Double? get() = altitudeBaroM ?: altitudeGpsM
}

data class StatistiquesVol(
    val nbPoints: Int,
    val distanceParcourueKm: Double,
    val altitudeMaxM: Double?,
    val vitesseMaxKmh: Double?,
    val vitesseMoyenneKmh: Double?,
    val ecartMaxRouteKm: Double?,
    val dureeEnregistree: Duration,
)

/**
 * Analyse d'une trace enregistrée (cahier des charges §4.3 et §7.7).
 *
 * Distance parcourue = somme des segments entre points consécutifs. Un segment est ignoré
 * (trou de réception ou point aberrant) si :
 *  - plus de 10 minutes séparent les deux points, ou
 *  - la vitesse impliquée par le segment dépasse [VITESSE_MAX_PLAUSIBLE_KMH].
 *
 * Note : le cahier des charges proposait un seuil fixe de 2 km entre deux points. À 900 km/h avec
 * une localisation toutes les 15 s, deux points consécutifs sont distants de 3,75 km : un seuil
 * fixe écarterait toute la croisière. Le critère de vitesse impliquée est équivalent dans
 * l'esprit (écarter les sauts impossibles) et reste valable quelle que soit la fréquence.
 */
object AnalyseTrace {
    const val TROU_MAX_MS: Long = 10L * 60L * 1000L
    const val VITESSE_MAX_PLAUSIBLE_KMH = 1_500.0

    fun segmentValide(a: PointTrace, b: PointTrace): Boolean {
        val dtMs = b.horodatageMs - a.horodatageMs
        if (dtMs <= 0 || dtMs > TROU_MAX_MS) return false
        val dKm = Geo.distanceKm(a.lat, a.lon, b.lat, b.lon)
        val vKmh = dKm / (dtMs / 3_600_000.0)
        return vKmh <= VITESSE_MAX_PLAUSIBLE_KMH
    }

    fun distanceParcourueKm(points: List<PointTrace>): Double {
        var total = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (segmentValide(a, b)) total += Geo.distanceKm(a.lat, a.lon, b.lat, b.lon)
        }
        return total
    }

    fun statistiques(points: List<PointTrace>, depart: Coordonnee?, arrivee: Coordonnee?): StatistiquesVol {
        val tries = points.sortedBy { it.horodatageMs }
        val distance = distanceParcourueKm(tries)
        val altMax = tries.mapNotNull { it.altitudeAfficheeM }.maxOrNull()
        val vMax = tries.mapNotNull { it.vitesseSolKmh }.maxOrNull()
        val duree = if (tries.size >= 2) (tries.last().horodatageMs - tries.first().horodatageMs).milliseconds else Duration.ZERO
        val heures = duree.inWholeSeconds / 3600.0
        val vMoy = if (heures > 0 && distance > 0) distance / heures else null
        val ecart = if (depart != null && arrivee != null && tries.isNotEmpty()) {
            tries.maxOf { Geo.ecartRouteKm(it.coordonnee, depart, arrivee) }
        } else null
        return StatistiquesVol(
            nbPoints = tries.size,
            distanceParcourueKm = distance,
            altitudeMaxM = altMax,
            vitesseMaxKmh = vMax,
            vitesseMoyenneKmh = vMoy,
            ecartMaxRouteKm = ecart,
            dureeEnregistree = duree,
        )
    }
}
