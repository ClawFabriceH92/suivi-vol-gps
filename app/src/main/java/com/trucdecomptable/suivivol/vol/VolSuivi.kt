package com.trucdecomptable.suivivol.vol

import com.trucdecomptable.suivivol.data.Aeroport
import com.trucdecomptable.suivivol.data.Geo
import kotlin.math.roundToInt

/**
 * Calculs d'avancement du vol, purs et sans Android : distance et durée
 * restantes, progression du trajet et résumé lisible. Tout est testable en
 * JUnit avec des valeurs de référence (le grand cercle est dans [Geo]).
 */
object VolSuivi {

    /**
     * Distance grand-cercle restante (en km) entre la position courante et
     * l'aéroport d'arrivée.
     */
    fun distanceRestanteKm(lat: Double, lon: Double, arrivee: Aeroport): Double =
        Geo.distanceKm(lat, lon, arrivee.lat, arrivee.lon)

    /**
     * Durée restante estimée, en minutes entières.
     *
     * - distance nulle → `0` (déjà arrivé, quelle que soit la vitesse) ;
     * - sinon, vitesse de référence = la vitesse moyenne si elle est fournie et
     *   exploitable (> 0), sinon la vitesse sol instantanée ;
     * - aucune vitesse exploitable (nulle ou absente) → `null` : on ne peut
     *   rien affirmer (appareil au sol ou GPS perdu).
     */
    fun dureeRestanteMin(distanceKm: Double, vitesseSolKmh: Double, vitesseMoyenneKmh: Double?): Int? {
        if (distanceKm <= 0.0) return 0
        val vitesse = when {
            vitesseMoyenneKmh != null && vitesseMoyenneKmh > 0.0 -> vitesseMoyenneKmh
            vitesseSolKmh > 0.0 -> vitesseSolKmh
            else -> return null
        }
        return (distanceKm / vitesse * 60.0).roundToInt()
    }

    /**
     * Part du trajet déjà parcourue, entre 0 et 1 : `1 − distance restante /
     * distance totale`. Sans départ ni arrivée, on ne peut pas calculer → 0 ;
     * un trajet de longueur nulle (départ = arrivée) est considéré comme
     * terminé → 1. La valeur est bornée : une position « au-delà » de
     * l'arrivée ne peut pas donner une progression négative.
     */
    fun progression(depart: Aeroport?, arrivee: Aeroport?, lat: Double, lon: Double): Double {
        if (depart == null || arrivee == null) return 0.0
        val totale = Geo.distanceKm(depart.lat, depart.lon, arrivee.lat, arrivee.lon)
        if (totale <= 0.0) return 1.0
        val restante = Geo.distanceKm(lat, lon, arrivee.lat, arrivee.lon)
        return (1.0 - restante / totale).coerceIn(0.0, 1.0)
    }

    /**
     * Résumé lisible en une ligne, du type « 412 km · 38 min · 640 km/h ».
     */
    fun resume(distanceKm: Double, minutes: Int, vitesseKmh: Double): String =
        "${distanceKm.roundToInt()} km · $minutes min · ${vitesseKmh.roundToInt()} km/h"
}
