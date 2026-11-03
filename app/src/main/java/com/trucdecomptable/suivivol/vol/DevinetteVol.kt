package com.trucdecomptable.suivivol.vol

import com.trucdecomptable.suivivol.data.Aeroport
import com.trucdecomptable.suivivol.data.Aeroports
import com.trucdecomptable.suivivol.data.Geo
import kotlin.math.abs
import kotlin.math.max

/**
 * Devine le vol (aéroports de départ et d'arrivée) à partir des seuls points
 * GPS, sans réseau. Les règles viennent du SPEC (§1 « Devinette du vol ») :
 *
 * - décollage quand vitesse sol > 150 km/h **et** altitude > 1 000 m tenus 60 s ;
 * - départ = dernier aéroport à moins de 15 km de la position au sol avant le
 *   décollage, mémorisé à chaque point tant qu'on est au sol ;
 * - candidats en vol = aéroports à plus de 100 km dans un cône de ±15° autour du
 *   cap, score = écart de cap + détour (distance via la position − distance directe) ;
 * - en descente (vitesse verticale < −3 m/s), l'aéroport le plus proche du point
 *   d'impact projeté prend la tête ;
 * - confiance normalisée entre 0 et 1, d'autant plus haute que le premier
 *   candidat distance le deuxième ;
 * - jamais de devinette au sol : l'état reste [PhaseVol.SOL], sans candidat, en
 *   conservant le départ mémorisé.
 */
class DevinetteVol(private val aeroports: List<Aeroport>) {

    private var phase: PhaseVol = PhaseVol.SOL
    private var departMemorise: Aeroport? = null
    private var aDecolle = false
    private var decolleDepuisMs: Long? = null

    /** Intègre un nouveau point GPS et renvoie l'état du vol deviné. */
    fun ajouter(point: PointVol): EtatVol {
        // --- Tant que le décollage n'est pas confirmé, on est au sol. ---
        if (!aDecolle) {
            // Mémorise le dernier aéroport à moins de 15 km de la position.
            val proche = aeroportLePlusProche(point.lat, point.lon, RAYON_DEPART_KM)
            if (proche != null) departMemorise = proche

            // Détection du décollage : conditions tenues 60 s sans interruption.
            val conditions = point.vitesseSolKmh > VITESSE_DECOLLAGE_KMH &&
                point.altitudeM > ALTITUDE_DECOLLAGE_M
            if (conditions) {
                val debut = decolleDepuisMs ?: point.horodatageMs
                decolleDepuisMs = debut
                if (point.horodatageMs - debut >= DUREE_DECOLLAGE_MS) {
                    aDecolle = true
                    decolleDepuisMs = null
                }
            } else {
                // Conditions rompues : on repart de zéro (le 60 s doit être continu).
                decolleDepuisMs = null
            }
        }

        // --- Jamais de devinette au sol : état dégradé à SOL, sans candidat,
        // mais on garde le départ mémorisé. ---
        if (!aDecolle) {
            phase = PhaseVol.SOL
            return EtatVol(PhaseVol.SOL, departMemorise, null, emptyList(), 0.0)
        }

        // --- En vol : phase, candidats et confiance. ---
        val phaseCourante = phaseEnVol(point)
        phase = phaseCourante

        val candidats = if (phaseCourante == PhaseVol.DESCENTE) {
            candidatsDescente(point)
        } else {
            candidatsCroisiere(point)
        }

        val meilleur = candidats.firstOrNull()
        return EtatVol(phaseCourante, departMemorise, meilleur?.aeroport, candidats, confiance(candidats))
    }

    /**
     * Phase déduite du point courant. L'atterrissage (ARRIVE) est détecté quand
     * l'avion est redescendu près du sol avec une vitesse verticale quasi nulle.
     */
    private fun phaseEnVol(point: PointVol): PhaseVol = when {
        point.altitudeM < ALTITUDE_ARRIVEE_M &&
            point.vitesseSolKmh < VITESSE_ARRIVEE_KMH &&
            abs(point.vitesseVerticaleMs) < 2.0 -> PhaseVol.ARRIVE

        point.vitesseVerticaleMs < -3.0 -> PhaseVol.DESCENTE
        point.vitesseVerticaleMs > 3.0 -> PhaseVol.MONTEE
        point.altitudeM >= ALTITUDE_DECOLLAGE_M -> PhaseVol.CROISIERE
        else -> PhaseVol.DECOLLAGE
    }

    /**
     * Candidats en montée / croisière : aéroports à plus de 100 km, dans un cône
     * de ±15° autour du cap. Score = écart de cap (degrés) + détour (km).
     */
    private fun candidatsCroisiere(point: PointVol): List<Candidat> {
        return aeroports
            .mapNotNull { a ->
                val distance = Geo.distanceKm(point.lat, point.lon, a.lat, a.lon)
                if (distance < DISTANCE_CANDIDAT_MIN_KM) return@mapNotNull null
                val capVersA = Geo.capDeg(point.lat, point.lon, a.lat, a.lon)
                val ecart = Geo.ecartCapDeg(point.capDeg, capVersA)
                if (ecart >= ECART_CAP_MAX_DEG) return@mapNotNull null
                Candidat(a, ecart + detourKm(point, a))
            }
            .sortedBy { it.score }
            .take(MAX_CANDIDATS)
    }

    /**
     * Candidats en descente : on projette le point d'impact (position + cap +
     * distance restante estimée depuis l'altitude et la pente), puis on classe
     * les aéroports par distance à ce point. Le plus proche prend la tête.
     */
    private fun candidatsDescente(point: PointVol): List<Candidat> {
        val pente = abs(point.vitesseVerticaleMs)
        if (pente < 0.5) return emptyList()

        // Temps estimé avant de toucher le sol, puis distance horizontale parcourue
        // d'ici là (la vitesse sol est en km/h, la vitesse verticale en m/s).
        val tempsAuSolS = point.altitudeM / pente
        val distanceRestanteKm = (point.vitesseSolKmh / 3.6) * tempsAuSolS / 1000.0
        val impact = Geo.pointApres(point.lat, point.lon, point.capDeg, distanceRestanteKm)

        return aeroports
            .map { a ->
                Candidat(a, Geo.distanceKm(impact.first, impact.second, a.lat, a.lon))
            }
            .sortedBy { it.score }
            .take(MAX_CANDIDATS)
    }

    /**
     * Détour : distance « départ → position → aéroport » moins la distance
     * directe « départ → aéroport ». Nul si aucun départ n'est encore mémorisé
     * (vol deviné en cours de route). Le grand cercle étant le plus court chemin,
     * le détour ne peut pas être négatif.
     */
    private fun detourKm(point: PointVol, aeroport: Aeroport): Double {
        val depart = departMemorise ?: return 0.0
        val directe = Geo.distanceKm(depart.lat, depart.lon, aeroport.lat, aeroport.lon)
        val via = Geo.distanceKm(depart.lat, depart.lon, point.lat, point.lon) +
            Geo.distanceKm(point.lat, point.lon, aeroport.lat, aeroport.lon)
        return max(0.0, via - directe)
    }

    /**
     * Confiance normalisée dans [0, 1] : écart relatif entre le score du premier
     * candidat et celui du deuxième. Plus le premier est nettement devant, plus
     * la devinette est fiable. Sans au moins deux candidats, pas de comparaison
     * possible : confiance nulle.
     */
    private fun confiance(candidats: List<Candidat>): Double {
        if (candidats.size < 2) return 0.0
        val premier = candidats[0].score
        val deuxieme = candidats[1].score
        if (deuxieme <= 0.0) return 0.0
        return (1.0 - premier / deuxieme).coerceIn(0.0, 1.0)
    }

    private fun aeroportLePlusProche(lat: Double, lon: Double, rayonKm: Double): Aeroport? {
        return Aeroports.proches(aeroports, lat, lon, rayonKm).firstOrNull()
    }

    private companion object {
        const val VITESSE_DECOLLAGE_KMH = 150.0
        const val ALTITUDE_DECOLLAGE_M = 1000.0
        const val DUREE_DECOLLAGE_MS = 60_000L
        const val RAYON_DEPART_KM = 15.0
        const val DISTANCE_CANDIDAT_MIN_KM = 100.0
        const val ECART_CAP_MAX_DEG = 15.0
        const val ALTITUDE_ARRIVEE_M = 100.0
        const val VITESSE_ARRIVEE_KMH = 100.0
        const val MAX_CANDIDATS = 10
    }
}

/**
 * État du vol deviné, renvoyé après chaque point GPS : phase courante, aéroports
 * de départ/arrivée, liste des candidats (du meilleur au moins bon) et confiance.
 */
data class EtatVol(
    val phase: PhaseVol,
    val depart: Aeroport?,
    val arrivee: Aeroport?,
    val candidats: List<Candidat>,
    val confiance: Double,
)

/**
 * Un aéroport candidat à l'arrivée, avec son score (le plus petit est le meilleur).
 */
data class Candidat(val aeroport: Aeroport, val score: Double)
