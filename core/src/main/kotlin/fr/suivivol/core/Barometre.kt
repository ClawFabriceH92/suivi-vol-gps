package fr.suivivol.core

import kotlin.math.pow

/**
 * Altitude barométrique (formule de l'atmosphère standard ICAO) et recalage sur le GPS.
 * Cahier des charges §7.3 : h = 44 330 × (1 − (P / 1013,25)^0,1903), P en hPa.
 */
object Barometre {
    const val PRESSION_REFERENCE_HPA = 1013.25

    fun altitudeM(pressionHpa: Double): Double =
        44_330.0 * (1.0 - (pressionHpa / PRESSION_REFERENCE_HPA).pow(0.1903))
}

/**
 * Recalage de l'altitude barométrique sur l'altitude GPS :
 * décalage = altitudeGpsFiable − altitudeBaro, lissé sur `taille` mesures, plafonné à ±`plafondM`.
 * altitudeAffichée = altitudeBaro + décalage.
 */
class RecalageBarometrique(private val taille: Int = 10, private val plafondM: Double = 150.0) {
    private val decalages = ArrayDeque<Double>()

    val nombreMesures: Int get() = decalages.size

    fun ajouterMesure(altitudeGpsFiableM: Double, altitudeBaroBruteM: Double) {
        decalages.addLast(altitudeGpsFiableM - altitudeBaroBruteM)
        while (decalages.size > taille) decalages.removeFirst()
    }

    /** Décalage courant en mètres (0 tant qu'aucune mesure n'a été fournie). */
    val decalageM: Double
        get() {
            if (decalages.isEmpty()) return 0.0
            val moyenne = decalages.sum() / decalages.size
            return moyenne.coerceIn(-plafondM, plafondM)
        }

    fun altitudeAffichee(altitudeBaroBruteM: Double): Double = altitudeBaroBruteM + decalageM

    fun reinitialiser() = decalages.clear()
}
