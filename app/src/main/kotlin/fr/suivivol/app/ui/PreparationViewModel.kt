package fr.suivivol.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.suivivol.app.data.Graphe
import fr.suivivol.app.data.db.VolEntity
import fr.suivivol.app.suivi.ServiceSuiviVol
import fr.suivivol.app.suivi.SuiviVolEtat
import fr.suivivol.core.Aeroport
import fr.suivivol.core.PlanDeVol
import fr.suivivol.core.SourceVol
import fr.suivivol.core.SourceVolManuelle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'écran de préparation. */
data class EtatPreparation(
    val requeteDepart: String = "",
    val resultatsDepart: List<Aeroport> = emptyList(),
    val depart: Aeroport? = null,
    val requeteArrivee: String = "",
    val resultatsArrivee: List<Aeroport> = emptyList(),
    val arrivee: Aeroport? = null,
    val decolleLeMs: Long = System.currentTimeMillis(),
    val erreur: String? = null,
    val demarrageEnCours: Boolean = false,
) {
    val plan: PlanDeVol?
        get() = if (depart != null && arrivee != null && depart.oaci != arrivee.oaci) PlanDeVol(depart, arrivee, decolleLeMs) else null

    val memeAeroport: Boolean get() = depart != null && arrivee != null && depart.oaci == arrivee.oaci
}

class PreparationViewModel : ViewModel() {
    private val _etat = MutableStateFlow(EtatPreparation())
    val etat: StateFlow<EtatPreparation> = _etat.asStateFlow()

    val historique: StateFlow<List<VolEntity>> = Graphe.db.volDao().tousLesVols()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Source du plan de vol : manuelle en v1, API en v2 (cahier des charges §1). */
    val source: SourceVol = SourceVolManuelle { _etat.value.plan }

    private var rechercheDepart: Job? = null
    private var rechercheArrivee: Job? = null

    init {
        viewModelScope.launch { Graphe.aeroports.charger() }
    }

    fun saisirDepart(texte: String) {
        _etat.update { it.copy(requeteDepart = texte, depart = null, erreur = null) }
        rechercheDepart?.cancel()
        rechercheDepart = viewModelScope.launch {
            delay(120)
            val resultats = Graphe.aeroports.rechercher(texte)
            _etat.update { it.copy(resultatsDepart = resultats) }
        }
    }

    fun choisirDepart(a: Aeroport) {
        _etat.update { it.copy(depart = a, requeteDepart = a.code, resultatsDepart = emptyList(), erreur = null) }
    }

    fun saisirArrivee(texte: String) {
        _etat.update { it.copy(requeteArrivee = texte, arrivee = null, erreur = null) }
        rechercheArrivee?.cancel()
        rechercheArrivee = viewModelScope.launch {
            delay(120)
            val resultats = Graphe.aeroports.rechercher(texte)
            _etat.update { it.copy(resultatsArrivee = resultats) }
        }
    }

    fun choisirArrivee(a: Aeroport) {
        _etat.update { it.copy(arrivee = a, requeteArrivee = a.code, resultatsArrivee = emptyList(), erreur = null) }
    }

    fun inverser() {
        _etat.update {
            it.copy(
                depart = it.arrivee,
                arrivee = it.depart,
                requeteDepart = it.arrivee?.code ?: it.requeteArrivee,
                requeteArrivee = it.depart?.code ?: it.requeteDepart,
                resultatsDepart = emptyList(),
                resultatsArrivee = emptyList(),
            )
        }
    }

    fun definirHeureDecollage(epochMs: Long) {
        _etat.update { it.copy(decolleLeMs = epochMs) }
    }

    fun signalerErreur(message: String) {
        _etat.update { it.copy(erreur = message) }
    }

    /** Crée le vol en base, l'enregistre comme vol actif et démarre le service de suivi. */
    fun decoller(onDemarre: (Long) -> Unit) {
        val courant = _etat.value
        val plan = courant.plan
        if (plan == null) {
            _etat.update { it.copy(erreur = if (it.memeAeroport) "Choisissez deux aéroports différents" else "Choisissez un aéroport de départ et un aéroport d'arrivée") }
            return
        }
        if (courant.demarrageEnCours) return
        _etat.update { it.copy(demarrageEnCours = true, erreur = null) }
        viewModelScope.launch {
            try {
                val dao = Graphe.db.volDao()
                val sequence = dao.derniereSequence() + 1
                val id = dao.insererVol(
                    VolEntity(
                        aeroportDepart = plan.depart.code,
                        aeroportArrivee = plan.arrivee.code,
                        decolleLe = plan.decolleLeMs,
                        distanceTheoriqueKm = plan.distanceTheoriqueKm,
                        sequence = sequence,
                    ),
                )
                Graphe.reglages.definirVolActif(id)
                SuiviVolEtat.reinitialiser()
                SuiviVolEtat.mettreAJour { it.copy(volId = id, plan = plan, actif = true) }
                ServiceSuiviVol.demarrer(Graphe.application, id)
                _etat.update { it.copy(demarrageEnCours = false) }
                onDemarre(id)
            } catch (e: Exception) {
                _etat.update { it.copy(demarrageEnCours = false, erreur = "Impossible de démarrer le vol : ${e.message}") }
            }
        }
    }
}
