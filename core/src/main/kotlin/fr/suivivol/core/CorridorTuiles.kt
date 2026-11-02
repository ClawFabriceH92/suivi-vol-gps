package fr.suivivol.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Identifiant d'une tuile « slippy map » (schéma OpenStreetMap z/x/y). */
data class Tuile(val z: Int, val x: Int, val y: Int)

/**
 * Calcule les tuiles de carte à mettre en cache le long d'un corridor autour du grand cercle
 * entre deux points (cahier des charges §4.1 : largeur ≈ 250 km).
 */
object CorridorTuiles {
    const val LARGEUR_DEFAUT_KM = 250.0
    const val ZOOM_MIN_DEFAUT = 3
    const val ZOOM_MAX_DEFAUT = 8

    /** Taille moyenne d'une tuile raster OSM retenue pour l'estimation (ordre de grandeur). */
    const val OCTETS_PAR_TUILE_ESTIMES = 15_000L

    private const val LAT_MAX = 85.0511

    fun tuileX(lon: Double, z: Int): Int {
        val n = 1 shl z
        val x = floor((lon + 180.0) / 360.0 * n).toInt()
        return ((x % n) + n) % n
    }

    fun tuileY(lat: Double, z: Int): Int {
        val n = 1 shl z
        val latR = lat.coerceIn(-LAT_MAX, LAT_MAX) * PI / 180.0
        val y = floor((1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * n).toInt()
        return y.coerceIn(0, n - 1)
    }

    fun tuiles(
        depart: Coordonnee,
        arrivee: Coordonnee,
        largeurKm: Double = LARGEUR_DEFAUT_KM,
        zoomMin: Int = ZOOM_MIN_DEFAUT,
        zoomMax: Int = ZOOM_MAX_DEFAUT,
    ): List<Tuile> {
        val distanceKm = Geo.distanceKm(depart, arrivee)
        val pas = 40.0
        val nbSegments = maxOf(1, (distanceKm / pas).toInt())
        val echantillons = Geo.pointsGrandCercle(depart, arrivee, nbSegments)
        val demiLargeur = largeurKm / 2.0
        val resultat = LinkedHashSet<Tuile>()
        for (z in zoomMin..zoomMax) {
            val n = 1 shl z
            for (p in echantillons) {
                val dLat = demiLargeur / 111.32
                val cosLat = cos(p.lat * PI / 180.0).coerceAtLeast(0.05)
                val dLon = demiLargeur / (111.32 * cosLat)
                val yMin = tuileY(p.lat + dLat, z)
                val yMax = tuileY(p.lat - dLat, z)
                val xDebut = tuileX(p.lon - dLon, z)
                val xFin = tuileX(p.lon + dLon, z)
                val largeurX = if (dLon * 2 >= 360.0) n else (((xFin - xDebut) % n) + n) % n + 1
                for (i in 0 until minOf(largeurX, n)) {
                    val x = (xDebut + i) % n
                    for (y in yMin..yMax) resultat.add(Tuile(z, x, y))
                }
            }
        }
        return resultat.toList()
    }

    fun estimationOctets(nbTuiles: Int): Long = nbTuiles * OCTETS_PAR_TUILE_ESTIMES
}
