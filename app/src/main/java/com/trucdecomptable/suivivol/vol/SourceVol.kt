package com.trucdecomptable.suivivol.vol

import com.trucdecomptable.suivivol.data.Aeroport

/**
 * Source du couple départ/arrivée d'un vol.
 *
 * Cette interface est le contrat unique imposé à l'écran de préparation : la
 * saisie manuelle (v1) et la récupération automatique via API (v2) l'implémentent
 * toutes les deux, si bien que l'ajout de la v2 ne cassera rien en aval.
 */
interface SourceVol {
    /** Aéroport de départ, `null` tant qu'il n'est pas connu. */
    val depart: Aeroport?

    /** Aéroport d'arrivée, `null` tant qu'il n'est pas connu. */
    val arrivee: Aeroport?

    /** D'où vient la valeur : saisie manuelle ou devinée. */
    val origine: OrigineVol

    /** Confiance dans la devinette, normalisée entre 0 (aucune) et 1 (certaine). */
    val confiance: Double
}
