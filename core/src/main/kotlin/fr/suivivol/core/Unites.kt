package fr.suivivol.core

import java.util.Locale

enum class UniteAltitude { METRES, PIEDS }
enum class UniteVitesse { KMH, NOEUDS }

/** Conversions et formatage des unités. */
object Unites {
    const val PIED_EN_M = 0.3048
    const val NOEUD_EN_KMH = 1.852

    fun mVersFt(m: Double): Double = m / PIED_EN_M
    fun kmhVersNoeuds(kmh: Double): Double = kmh / NOEUD_EN_KMH

    /** Groupement des milliers par une espace fine insécable, sans décimale. */
    fun entier(v: Double): String {
        val arrondi = Math.round(v)
        val brut = String.format(Locale.ROOT, "%,d", arrondi)
        return brut.replace(',', ' ')
    }

    fun formatAltitude(m: Double?, unite: UniteAltitude): String {
        if (m == null) return "—"
        return when (unite) {
            UniteAltitude.METRES -> "${entier(m)} m"
            UniteAltitude.PIEDS -> "${entier(mVersFt(m))} ft"
        }
    }

    fun formatVitesse(kmh: Double?, unite: UniteVitesse): String {
        if (kmh == null) return "—"
        return when (unite) {
            UniteVitesse.KMH -> "${entier(kmh)} km/h"
            UniteVitesse.NOEUDS -> "${entier(kmhVersNoeuds(kmh))} kt"
        }
    }

    fun formatDistanceKm(km: Double?): String {
        if (km == null) return "—"
        return if (km < 10) String.format(Locale.FRANCE, "%.1f km", km) else "${entier(km)} km"
    }
}
