package fr.suivivol.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Coordonnée WGS84 en degrés décimaux. */
data class Coordonnee(val lat: Double, val lon: Double)

/**
 * Calculs géodésiques sur une sphère (haversine, cap orthodromique, points intermédiaires).
 * Rayon terrestre moyen : 6 371 008,8 m (cahier des charges §7.1).
 */
object Geo {
    const val RAYON_TERRE_M = 6_371_008.8

    private fun rad(deg: Double) = deg * PI / 180.0
    private fun deg(rad: Double) = rad * 180.0 / PI

    /** Distance grand-cercle (haversine) en mètres. */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = rad(lat1)
        val p2 = rad(lat2)
        val dp = p2 - p1
        val dl = rad(lon2 - lon1)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * RAYON_TERRE_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        distanceM(lat1, lon1, lat2, lon2) / 1000.0

    fun distanceKm(a: Coordonnee, b: Coordonnee): Double = distanceKm(a.lat, a.lon, b.lat, b.lon)

    /** Cap initial orthodromique en degrés, ramené dans [0, 360[. */
    fun capInitialDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = rad(lat1)
        val p2 = rad(lat2)
        val dl = rad(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return normaliserCap(deg(atan2(y, x)))
    }

    fun capInitialDeg(a: Coordonnee, b: Coordonnee): Double = capInitialDeg(a.lat, a.lon, b.lat, b.lon)

    fun normaliserCap(cap: Double): Double {
        var c = cap % 360.0
        if (c < 0) c += 360.0
        return if (c >= 360.0) 0.0 else c
    }

    /** Point intermédiaire sur le grand cercle, pour une fraction f dans [0, 1]. */
    fun pointIntermediaire(a: Coordonnee, b: Coordonnee, fraction: Double): Coordonnee {
        val f = fraction.coerceIn(0.0, 1.0)
        val p1 = rad(a.lat)
        val l1 = rad(a.lon)
        val p2 = rad(b.lat)
        val l2 = rad(b.lon)
        val d = distanceM(a.lat, a.lon, b.lat, b.lon) / RAYON_TERRE_M
        if (d < 1e-12) return a
        val sa = sin((1 - f) * d) / sin(d)
        val sb = sin(f * d) / sin(d)
        val x = sa * cos(p1) * cos(l1) + sb * cos(p2) * cos(l2)
        val y = sa * cos(p1) * sin(l1) + sb * cos(p2) * sin(l2)
        val z = sa * sin(p1) + sb * sin(p2)
        val lat = atan2(z, sqrt(x * x + y * y))
        val lon = atan2(y, x)
        return Coordonnee(deg(lat), deg(lon))
    }

    /** Échantillonne le grand cercle en `nbSegments` segments (nbSegments + 1 points). */
    fun pointsGrandCercle(a: Coordonnee, b: Coordonnee, nbSegments: Int = 64): List<Coordonnee> {
        val n = nbSegments.coerceAtLeast(1)
        return (0..n).map { i -> pointIntermediaire(a, b, i.toDouble() / n) }
    }

    /**
     * Écart (distance « cross-track ») entre un point et le grand cercle qui passe par `a` et `b`,
     * en km, toujours positif.
     */
    fun ecartRouteKm(point: Coordonnee, a: Coordonnee, b: Coordonnee): Double {
        val d13 = distanceM(a.lat, a.lon, point.lat, point.lon) / RAYON_TERRE_M
        val t13 = rad(capInitialDeg(a, point))
        val t12 = rad(capInitialDeg(a, b))
        val dxt = asin((sin(d13) * sin(t13 - t12)).coerceIn(-1.0, 1.0))
        return abs(dxt) * RAYON_TERRE_M / 1000.0
    }

    /** Rose des vents simplifiée (8 directions) en abréviations françaises. */
    fun roseDesVents(capDeg: Double): String {
        val secteurs = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        val index = ((normaliserCap(capDeg) + 22.5) / 45.0).toInt() % 8
        return secteurs[index]
    }

    /** Exemple : « 274° O ». */
    fun formatCap(capDeg: Double): String {
        val c = normaliserCap(capDeg)
        val arrondi = Math.round(c).toInt() % 360
        return "$arrondi° ${roseDesVents(c)}"
    }
}
