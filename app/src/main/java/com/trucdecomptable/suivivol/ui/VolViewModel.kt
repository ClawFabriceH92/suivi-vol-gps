package com.trucdecomptable.suivivol.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.os.Looper
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
 * Détection automatique de la situation, faite à l'ouverture de l'application :
 * position GPS et aéroport de départ le plus proche. L'utilisateur n'a plus rien
 * à choisir avant de démarrer.
 */
data class Detection(
    val enCours: Boolean = true,
    val aeroport: Aeroport? = null,
    val distanceKm: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val erreur: String? = null,
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

    private val _detection = MutableStateFlow(Detection())
    val detection: StateFlow<Detection> = _detection.asStateFlow()

    init {
        // Dès l'ouverture : on cherche la position et l'aéroport de départ, sans
        // rien demander à l'utilisateur.
        lancerDetection()
    }

    /**
     * Cherche une position puis l'aéroport le plus proche. Abonnement coupé dès
     * le premier point trouvé (ou au bout de 20 s). Permission refusée : message
     * clair, aucune exception.
     */
    @SuppressLint("MissingPermission")
    fun lancerDetection() {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _detection.value = Detection(enCours = false, erreur = "Localisation refusée : autorise l'accès à la position pour la détection automatique.")
            return
        }
        _detection.value = Detection(enCours = true)

        // Position déjà connue : réponse immédiate, pas besoin d'attendre un satellite.
        val connue = positionConnue()
        if (connue != null) {
            appliquerDetection(connue.latitude, connue.longitude)
            return
        }
        try {
            gestionnaire.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, ecouteurDetection, Looper.getMainLooper()
            )
            gestionnaire.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 1000L, 0f, ecouteurDetection, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            _detection.value = Detection(enCours = false, erreur = "Accès à la position refusé.")
            return
        } catch (e: IllegalArgumentException) {
            _detection.value = Detection(enCours = false, erreur = "GPS indisponible sur cet appareil.")
            return
        }
        // Garde-fou : on n'attend pas indéfiniment.
        principal.postDelayed({
            if (_detection.value.enCours) {
                _detection.value = _detection.value.copy(
                    enCours = false,
                    erreur = "Position pas encore disponible : tu peux démarrer quand même, le suivi s'accrochera en route.",
                )
                arreterDetection()
            }
        }, 20_000L)
    }

    private fun arreterDetection() {
        try {
            gestionnaire.removeUpdates(ecouteurDetection)
        } catch (_: Throwable) {
        }
    }

    private fun appliquerDetection(lat: Double, lon: Double) {
        arreterDetection()
        val trouve = Aeroports.plusProche(aeroports, lat, lon, RAYON_DETECTION_KM)
        if (trouve != null && !departManuel) {
            departActuel = trouve.first
            origineActuelle = OrigineVol.DEVINEE
        }
        _detection.value = Detection(
            enCours = false,
            aeroport = departActuel,
            distanceKm = trouve?.second,
            latitude = lat,
            longitude = lon,
        )
        if (trouve != null) JournalPlantage.etape("départ détecté (${trouve.first.code}, %.0f km)".format(trouve.second))
        publier()
    }

    private val ecouteurDetection = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            appliquerDetection(location.latitude, location.longitude)
        }
    }

    private var departActuel: Aeroport? = null
    private var arriveeActuelle: Aeroport? = null
    // Vrai dès que l'utilisateur choisit (ou corrige) l'arrivée à la main :
    // la devinette ne doit plus écraser son choix.
    private var arriveeManuelle = false
    // Vrai dès que l'utilisateur choisit un départ à la main : la déduction
    // depuis le GPS ne doit plus l'écraser.
    private var departManuel = false
    private var enVolActuel = false
    private var etatActuel: EtatVol? = null
    private var dernierPoint: PointVol? = null
    private val pointsJournal = mutableListOf<PointVol>()
    private var messageActuel: String? = null
    private var origineActuelle: OrigineVol = OrigineVol.MANUELLE

    private val principal = android.os.Handler(android.os.Looper.getMainLooper())
    // Détection d'atterrissage : on ne conclut qu'après avoir réellement volé.
    private var aVole = false
    private var arretDepuisMs: Long = 0L

    /** Recherche d'aéroports pour l'écran de préparation (code, nom ou ville). */
    fun rechercher(texte: String): List<Aeroport> = Aeroports.rechercher(aeroports, texte)

    /** Choix manuel du départ : toujours possible, repasse l'origine en MANUELLE. */
    fun choisirDepart(aeroport: Aeroport) {
        departActuel = aeroport
        departManuel = true
        origineActuelle = OrigineVol.MANUELLE
        publier()
    }

    /**
     * Déduit l'aéroport de départ de la position GPS actuelle : on est souvent
     * déjà à l'aéroport quand on prépare le vol. Un aéroport à service régulier
     * est privilégié ([Aeroports.plusProche]). Aucun écrasement d'un choix
     * manuel, et message clair si rien n'est trouvé.
     */
    @SuppressLint("MissingPermission")
    fun devinerDepart() {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            messageActuel = "Localisation refusée : impossible de déduire le départ."
            publier()
            return
        }
        val position = positionConnue()
        if (position == null) {
            messageActuel = "Position inconnue pour l'instant : choisis le départ à la main " +
                "ou réessaie dans quelques secondes."
            publier()
            return
        }
        appliquerDepartDepuis(position.latitude, position.longitude, bavard = true)
    }

    /** Déduction silencieuse pendant le suivi : uniquement si rien n'est choisi. */
    private fun deduireDepartSiBesoin(lat: Double, lon: Double) {
        if (departManuel || departActuel != null) return
        appliquerDepartDepuis(lat, lon, bavard = false)
    }

    private fun appliquerDepartDepuis(lat: Double, lon: Double, bavard: Boolean) {
        val trouve = Aeroports.plusProche(aeroports, lat, lon, RAYON_DEPART_KM)
        if (trouve == null) {
            if (bavard) {
                messageActuel = "Aucun aéroport à moins de ${RAYON_DEPART_KM.toInt()} km : " +
                    "choisis le départ à la main."
                publier()
            }
            return
        }
        departActuel = trouve.first
        origineActuelle = OrigineVol.DEVINEE
        if (bavard) {
            messageActuel = "Départ déduit de ta position : %s (%.0f km)".format(trouve.first.nom, trouve.second)
        }
        JournalPlantage.etape("départ déduit par GPS (${trouve.first.code})")
        publier()
    }

    /** Dernière position connue, sans attendre un nouveau relevé. */
    @SuppressLint("MissingPermission")
    private fun positionConnue(): Location? = try {
        gestionnaire.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: gestionnaire.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
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

        // Départ : déduit de la position tant que l'utilisateur n'a rien choisi.
        deduireDepartSiBesoin(point.lat, point.lon)

        val etat = devinette.ajouter(point)
        etatActuel = etat

        // Arrivée devinée : retenue seulement si l'utilisateur n'a rien choisi
        // manuellement et si la devinette est assez confiante.
        if (!arriveeManuelle && etat.arrivee != null && etat.confiance >= SEUIL_CONFIANCE) {
            arriveeActuelle = etat.arrivee
            origineActuelle = OrigineVol.DEVINEE
        }

        // --- Atterrissage automatique ---
        // Une fois qu'on a vraiment volé, un arrêt prolongé au sol ferme le vol :
        // l'utilisateur n'a pas à appuyer sur « Arrêter » en sortant de l'avion.
        if (point.vitesseSolKmh >= VITESSE_VOL_KMH) aVole = true
        if (aVole && point.vitesseSolKmh < VITESSE_SOL_IMMOBILE_KMH) {
            if (arretDepuisMs == 0L) arretDepuisMs = point.horodatageMs
            if (point.horodatageMs - arretDepuisMs >= IMMOBILITE_MS) {
                JournalPlantage.etape("atterrissage détecté, vol clôturé")
                arreterSuivi()
                return
            }
        } else if (point.vitesseSolKmh >= VITESSE_SOL_IMMOBILE_KMH) {
            arretDepuisMs = 0L
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
        /** Rayon de déduction du départ : un aéroport proche de la position. */
        const val RAYON_DEPART_KM = 40.0

        /** Rayon retenu pour la détection automatique à l'ouverture. */
        const val RAYON_DETECTION_KM = 40.0

        /** Vitesse au-delà de laquelle on considère que l'appareil a volé. */
        const val VITESSE_VOL_KMH = 120.0

        /** En dessous, l'appareil est considéré à l'arrêt. */
        const val VITESSE_SOL_IMMOBILE_KMH = 30.0

        /** Durée d'immobilité qui vaut atterrissage. */
        const val IMMOBILITE_MS = 3 * 60_000L
    }
}
