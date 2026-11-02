package fr.suivivol.app.suivi

import fr.suivivol.core.Coordonnee
import fr.suivivol.core.DureeRestante
import fr.suivivol.core.PlanDeVol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** État courant du suivi, publié par le service et observé par l'écran de vol. */
data class EtatVol(
    val volId: Long? = null,
    val plan: PlanDeVol? = null,
    val actif: Boolean = false,
    val position: Coordonnee? = null,
    val altitudeGpsM: Double? = null,
    /** Altitude barométrique recalée sur le GPS (null si pas de baromètre). */
    val altitudeBaroM: Double? = null,
    /** Altitude équivalente de la pression cabine quand la cabine est pressurisée (baro inutilisable). */
    val altitudeCabineM: Double? = null,
    val barometreDisponible: Boolean = false,
    val vitesseLisseeKmh: Double? = null,
    val vitesseInstantaneeKmh: Double? = null,
    val capDeg: Double? = null,
    val precisionM: Double? = null,
    val derniereFixMs: Long? = null,
    val distanceRestanteKm: Double? = null,
    val dureeRestante: DureeRestante = DureeRestante.Inconnue,
    val heureArriveeMs: Long? = null,
    val progression: Double = 0.0,
    val nbPoints: Int = 0,
    /** Trace parcourue, décimée pour l'affichage (un point tous les ~500 m). */
    val trace: List<Coordonnee> = emptyList(),
    val gpsActive: Boolean = true,
    val permissionRefusee: Boolean = false,
    val message: String? = null,
) {
    /** Altitude à afficher en gros : baromètre recalé si disponible, sinon GPS. */
    val altitudeAfficheeM: Double? get() = altitudeBaroM ?: altitudeGpsM
}

/** Partage de l'état entre le service au premier plan et l'interface. */
object SuiviVolEtat {
    private val _etat = MutableStateFlow(EtatVol())
    val etat: StateFlow<EtatVol> = _etat.asStateFlow()

    fun mettreAJour(transformation: (EtatVol) -> EtatVol) = _etat.update(transformation)

    fun reinitialiser() {
        _etat.value = EtatVol()
    }
}
