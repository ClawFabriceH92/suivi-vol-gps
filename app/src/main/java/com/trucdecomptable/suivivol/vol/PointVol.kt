package com.trucdecomptable.suivivol.vol

/**
 * Un point de trace GPS brut, tel qu'il sort du GNSS. Toutes les valeurs sont
 * déjà dans les unités utilisées par l'application (mètres, km/h, m/s) pour que
 * le reste du code n'ait aucune conversion à faire.
 */
data class PointVol(
    val lat: Double,
    val lon: Double,
    val altitudeM: Double,
    val vitesseSolKmh: Double,
    val capDeg: Double,
    val vitesseVerticaleMs: Double,
    val horodatageMs: Long,
)

/**
 * Phase du vol, déduite des seules mesures GPS (vitesse sol, altitude, vitesse
 * verticale). La [PhaseVol.SOL] est l'état initial et celui où aucune devinette
 * n'est produite.
 */
enum class PhaseVol { SOL, DECOLLAGE, MONTEE, CROISIERE, DESCENTE, ARRIVE }

/**
 * D'où vient le couple départ/arrivée : saisi à la main (voie de référence) ou
 * deviné à partir du GPS. Ne sert qu'à l'affichage et au pré-remplissage.
 */
enum class OrigineVol { MANUELLE, DEVINEE }
