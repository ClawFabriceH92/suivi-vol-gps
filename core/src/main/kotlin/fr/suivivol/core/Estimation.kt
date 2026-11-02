package fr.suivivol.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Résultat du calcul de durée restante. */
sealed class DureeRestante {
    data class Estimee(val duree: Duration) : DureeRestante()

    /** Vitesse nulle depuis plus de 5 minutes : appareil au sol ou GPS perdu. */
    data object EnAttenteDeMouvement : DureeRestante()

    /** Pas encore de position ou pas de destination. */
    data object Inconnue : DureeRestante()
}

/** Durées, progression et formatage (cahier des charges §7.5 et §7.6). */
object Estimation {
    const val VITESSE_PLANCHER_KMH = 200.0
    const val VITESSE_CROISIERE_DEFAUT_KMH = 850.0

    /**
     * Durée restante = distanceRestante / max(vitesse, 200 km/h).
     * Une vitesse inconnue (null) est traitée comme le plancher.
     */
    fun dureeRestante(distanceRestanteKm: Double, vitesseKmh: Double?): Duration {
        val v = maxOf(vitesseKmh ?: 0.0, VITESSE_PLANCHER_KMH)
        val heures = distanceRestanteKm.coerceAtLeast(0.0) / v
        return heures.hours
    }

    /** Durée théorique d'un trajet à une vitesse de croisière de référence. */
    fun dureeTheorique(distanceKm: Double, vitesseCroisiereKmh: Double): Duration {
        val v = if (vitesseCroisiereKmh > 0) vitesseCroisiereKmh else VITESSE_CROISIERE_DEFAUT_KMH
        return (distanceKm / v).hours
    }

    /** Progression du trajet dans [0, 1]. */
    fun progression(distanceRestanteKm: Double, distanceTotaleKm: Double): Double {
        if (distanceTotaleKm <= 0.0) return 0.0
        return (1.0 - distanceRestanteKm / distanceTotaleKm).coerceIn(0.0, 1.0)
    }

    /** « 2 h 35 min », « 35 min », « 12 h 05 min ». Arrondi à la minute la plus proche. */
    fun formatHMin(duree: Duration): String {
        val totalMinutes = Math.round(duree.inWholeSeconds / 60.0)
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h <= 0 -> "$m min"
            else -> "$h h ${m.toString().padStart(2, '0')} min"
        }
    }
}

/**
 * Détecte l'immobilité : vitesse nulle (ou absente) pendant plus de `seuil`.
 * On considère « en mouvement » toute vitesse supérieure à `vitesseMinKmh`.
 */
class DetecteurImmobilite(
    private val seuil: Duration = 5.minutes,
    private val vitesseMinKmh: Double = 5.0,
) {
    private var dernierMouvementMs: Long? = null
    private var premierSignalMs: Long? = null

    fun signaler(horodatageMs: Long, vitesseKmh: Double?) {
        if (premierSignalMs == null) premierSignalMs = horodatageMs
        if (vitesseKmh != null && vitesseKmh > vitesseMinKmh) dernierMouvementMs = horodatageMs
    }

    /** Vrai si aucun mouvement n'a été observé depuis plus de `seuil`. */
    fun estImmobile(maintenantMs: Long): Boolean {
        val reference = dernierMouvementMs ?: premierSignalMs ?: return false
        return maintenantMs - reference > seuil.inWholeMilliseconds
    }

    fun reinitialiser() {
        dernierMouvementMs = null
        premierSignalMs = null
    }
}
