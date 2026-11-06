package com.trucdecomptable.suivivol.vol

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Géométrie des cadrans du tableau de bord (pur, testable en JUnit).
 *
 * Convention : 0° = midi (haut du cadran), les angles augmentent dans le sens
 * horaire, comme sur un instrument d'avion. Compose dessine ses arcs dans le
 * sens horaire également, à partir de 3 heures (90°) : d'où le décalage de 90°
 * appliqué dans les composants.
 */
object CadranMath {

    /** Ramène un cap quelconque dans [0, 360[. */
    fun capNormalise(cap: Double): Double {
        var c = cap % 360.0
        if (c < 0) c += 360.0
        return c
    }

    /**
     * Angle d'aiguille (en degrés, 0 = haut, sens horaire) pour une valeur
     * comprise entre [min] et [max], projetée sur l'arc [angleDebut, angleFin].
     * Les valeurs hors bornes sont écrêtées : l'aiguille ne sort jamais du cadran.
     */
    fun angleAiguille(
        valeur: Double,
        min: Double,
        max: Double,
        angleDebut: Double = -120.0,
        angleFin: Double = 120.0,
    ): Double {
        if (max <= min) return angleDebut
        val ratio = ((valeur - min) / (max - min)).coerceIn(0.0, 1.0)
        return angleDebut + ratio * (angleFin - angleDebut)
    }

    /** Vrai si la valeur est exploitable (ni nulle par défaut, ni infinie). */
    fun valeurUtilisable(valeur: Double?): Boolean =
        valeur != null && !valeur.isNaN() && !valeur.isInfinite() && abs(valeur) < 1e6

    /** Écart de vitesse verticale (m/s) affiché lissé, borné à [borne]. */
    fun vitesseVerticaleBornee(ms: Double, borne: Double = 10.0): Double =
        ms.coerceIn(-borne, borne)

    /** Libellé d'une vitesse verticale, signe explicite, sans décimale inutile. */
    fun libelleVitesseVerticale(ms: Double?): String {
        if (!valeurUtilisable(ms)) return "—"
        val arrondi = ms!!.roundToInt()
        val signe = if (arrondi > 0) "+" else ""
        return "$signe$arrondi m/s"
    }

    /** Libellé d'altitude, arrondi au mètre, ou tiret si inconnue. */
    fun libelleAltitude(metres: Double?): String =
        if (!valeurUtilisable(metres)) "—" else "${metres!!.roundToInt()} m"

    /** Libellé de vitesse sol, arrondi au km/h, ou tiret si inconnue. */
    fun libelleVitesse(kmh: Double?): String =
        if (!valeurUtilisable(kmh)) "—" else "${kmh!!.roundToInt()} km/h"

    /** Libellé de cap sur trois chiffres (convention aéronautique : 045°). */
    fun libelleCap(cap: Double?): String =
        if (!valeurUtilisable(cap)) "—" else "%03d°".format(capNormalise(cap!!).roundToInt() % 360)

    /** Points cardinaux pour un cap donné (utile sous le cadran de cap). */
    fun pointCardinal(cap: Double): String {
        val secteurs = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        val index = ((capNormalise(cap) + 22.5) / 45.0).toInt() % 8
        return secteurs[index]
    }
}
