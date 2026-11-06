package com.trucdecomptable.suivivol.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.trucdecomptable.suivivol.data.Aeroport
import com.trucdecomptable.suivivol.data.Aeroports
import com.trucdecomptable.suivivol.vol.DevinetteVol
import com.trucdecomptable.suivivol.vol.EtatVol
import com.trucdecomptable.suivivol.vol.JournalGpx
import com.trucdecomptable.suivivol.vol.OrigineVol
import com.trucdecomptable.suivivol.vol.PointVol
import com.trucdecomptable.suivivol.util.JournalPlantage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État de l'écran de vol, exposé en [StateFlow] : les aéroports retenus, la
 * source (manuelle ou devinée), l'état courant du moteur de devinette, le
 * dernier point GPS, le journal des points et un éventuel message d'alerte
 * (permission refusée, fournisseur GPS absent…).
 */
data class EcranVol(
    val depart: Aeroport?,
    val arrivee: Aeroport?,
    val origine: OrigineVol,
    val enVol: Boolean,
    val etat: EtatVol?,
    val dernier: PointVol?,
    val points: List<PointVol>,
    val message: String?,
)

/**
 * Vue-modèle du suivi de vol : charge la base d'aéroports une seule fois,
 * s'abonne au GPS (GNSS natif via [LocationManager], sans Play Services),
 * alimente le moteur de devinette et expose l'état de l'écran.
 */
class VolViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application

    // Base d'aéroports chargée une seule fois (cache mémoire de Aeroports) ;
    // le moteur de devinette et le gestionnaire de localisation en dépendent.
    // Une base d'aéroports illisible ne doit pas fermer l'application : on
    // renvoie une liste vide et l'écran affichera un message explicite.
    private val aeroports: List<Aeroport> by lazy {
        try {
            Aeroports.charger(app).also { JournalPlantage.etape("base aéroports chargée (${it.size})") }
        } catch (t: Throwable) {
            JournalPlantage.etape("échec base aéroports : ${t.javaClass.simpleName}")
            messageActuel = "Base d'aéroports illisible : recherche indisponible, saisie manuelle possible."
            emptyList()
        }
    }
    private val devinette: DevinetteVol by lazy { DevinetteVol(aeroports) }
    private val gestionnaire: LocationManager by lazy {
        app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val _ecran = MutableStateFlow(
        EcranVol(null, null, OrigineVol.MANUELLE, false, null, null, emptyList(), null)
    )
    val ecran: StateFlow<EcranVol> = _ecran.asStateFlow()

    private var departActuel: Aeroport? = null
    private var arriveeActuelle: Aeroport? = null
    // Vrai dès que l'utilisateur choisit (ou corrige) l'arrivée à la main :
    // la devinette ne doit plus écraser son choix.
    private var arriveeManuelle = false
    private var enVolActuel = false
    private var etatActuel: EtatVol? = null
    private var dernierPoint: PointVol? = null
    private val pointsJournal = mutableListOf<PointVol>()
    private var messageActuel: String? = null
    private var origineActuelle: OrigineVol = OrigineVol.MANUELLE

    /** Recherche d'aéroports pour l'écran de préparation (code, nom ou ville). */
    fun rechercher(texte: String): List<Aeroport> = Aeroports.rechercher(aeroports, texte)

    /** Choix manuel du départ : toujours possible, repasse l'origine en MANUELLE. */
    fun choisirDepart(aeroport: Aeroport) {
        departActuel = aeroport
        origineActuelle = OrigineVol.MANUELLE
        publier()
    }

    /** Choix manuel de l'arrivée : verrouille la devinette et repasse en MANUELLE. */
    fun choisirArrivee(aeroport: Aeroport) {
        arriveeActuelle = aeroport
        arriveeManuelle = true
        origineActuelle = OrigineVol.MANUELLE
        publier()
    }

    /** Reprend la main sur une arrivée devinée : même effet qu'un choix manuel. */
    fun corrigerArrivee(aeroport: Aeroport) = choisirArrivee(aeroport)

    /**
     * Démarre l'abonnement GPS. Si la permission de localisation manque, on ne
     * plante pas : un message clair est posé dans l'état. Le fournisseur GPS
     * absent est traité de la même façon.
     */
    @SuppressLint("MissingPermission")
    fun demarrerSuivi() {
        // Contrôle de permission : refus = message explicite, pas de crash.
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            enVolActuel = false
            messageActuel =
                "Localisation refusée : active l'accès à la position dans les réglages pour suivre le vol."
            publier()
            return
        }
        try {
            gestionnaire.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, MIN_TEMPS_MS, MIN_DISTANCE_M, ecouteur
            )
            enVolActuel = true
            messageActuel = null
            publier()
        } catch (e: SecurityException) {
            // Ne devrait pas arriver après le contrôle, mais on ne plante jamais.
            messageActuel = "Impossible d'accéder à la position : permission manquante."
            publier()
        } catch (e: IllegalArgumentException) {
            // Fournisseur GPS absent de l'appareil.
            messageActuel = "Le fournisseur GPS n'est pas disponible sur cet appareil."
            publier()
        }
    }

    /** Arrête l'abonnement GPS. */
    fun arreterSuivi() {
        try {
            gestionnaire.removeUpdates(ecouteur)
        } catch (e: SecurityException) {
            // Rien à retirer ; on continue sans lever.
        }
        enVolActuel = false
        publier()
    }

    /** Journal GPX des points enregistrés, en chaîne (aucune écriture disque). */
    fun exportGpx(): String = JournalGpx.construire(pointsJournal.toList())

    /** Intègre un point GPS : conversion, devinette, éventuelle arrivée devinée. */
    private fun traiterPoint(location: Location) {
        val point = versPoint(location)
        dernierPoint = point
        pointsJournal.add(point)

        val etat = devinette.ajouter(point)
        etatActuel = etat

        // Arrivée devinée : retenue seulement si l'utilisateur n'a rien choisi
        // manuellement et si la devinette est assez confiante.
        if (!arriveeManuelle && etat.arrivee != null && etat.confiance >= SEUIL_CONFIANCE) {
            arriveeActuelle = etat.arrivee
            origineActuelle = OrigineVol.DEVINEE
        }
        publier()
    }

    /**
     * Convertit une [Location] Android en [PointVol], dans les unités de l'app
     * (mètres, km/h, degrés, m/s). Les valeurs absentes (altitude, vitesse,
     * cap) retombent sur des valeurs neutres au lieu de lever.
     */
    private fun versPoint(location: Location): PointVol {
        val altitude = if (location.hasAltitude()) location.altitude else 0.0
        val horodatage = if (location.time != 0L) location.time else System.currentTimeMillis()
        val vitesse = if (location.hasSpeed()) (location.speed.toDouble() * 3.6).coerceAtLeast(0.0) else 0.0
        val cap = if (location.hasBearing()) normaliserCap(location.bearing.toDouble()) else 0.0
        // Vitesse verticale estimée entre deux points successifs (variation
        // d'altitude divisée par le temps écoulé).
        val verticale = calculerVitesseVerticale(altitude, horodatage)
        return PointVol(location.latitude, location.longitude, altitude, vitesse, cap, verticale, horodatage)
    }

    /** Vitesse verticale entre le point précédent et celui-ci, en m/s. */
    private fun calculerVitesseVerticale(altitude: Double, horodatage: Long): Double {
        val precedent = dernierPoint ?: return 0.0
        val dureeS = (horodatage - precedent.horodatageMs) / 1000.0
        if (dureeS <= 0.0) return 0.0
        return (altitude - precedent.altitudeM) / dureeS
    }

    /** Ramène un cap éventuellement négatif dans [0, 360[. */
    private fun normaliserCap(cap: Double): Double {
        var c = cap
        while (c < 0.0) c += 360.0
        return c % 360.0
    }

    private fun publier() {
        _ecran.value = EcranVol(
            depart = departActuel,
            arrivee = arriveeActuelle,
            origine = origineActuelle,
            enVol = enVolActuel,
            etat = etatActuel,
            dernier = dernierPoint,
            points = pointsJournal.toList(),
            message = messageActuel,
        )
    }

    private val ecouteur = object : LocationListener {
        override fun onLocationChanged(location: Location) = traiterPoint(location)
    }

    private companion object {
        const val MIN_TEMPS_MS = 2000L
        const val MIN_DISTANCE_M = 10f
        const val SEUIL_CONFIANCE = 0.5
    }
}
