package fr.suivivol.core

import kotlin.time.Duration

/** Trajet théorique entre deux aéroports. */
data class PlanDeVol(
    val depart: Aeroport,
    val arrivee: Aeroport,
    val decolleLeMs: Long,
) {
    val distanceTheoriqueKm: Double get() = Geo.distanceKm(depart.coordonnee, arrivee.coordonnee)
    val capInitialDeg: Double get() = Geo.capInitialDeg(depart.coordonnee, arrivee.coordonnee)
    fun dureeTheorique(vitesseCroisiereKmh: Double): Duration = Estimation.dureeTheorique(distanceTheoriqueKm, vitesseCroisiereKmh)
    val libelle: String get() = "${depart.code} → ${arrivee.code}"
}

/**
 * Source d'un plan de vol. Aujourd'hui : saisie manuelle. Demain : récupération automatique
 * par un numéro de vol (v2). L'écran de préparation ne dépend que de cette interface.
 */
interface SourceVol {
    val nom: String
    suspend fun proposer(): PlanDeVol?
}

/** Source manuelle : le plan de vol est exactement ce que l'utilisateur a saisi. */
class SourceVolManuelle(private val fournisseur: () -> PlanDeVol?) : SourceVol {
    override val nom: String = "Saisie manuelle"
    override suspend fun proposer(): PlanDeVol? = fournisseur()
}
