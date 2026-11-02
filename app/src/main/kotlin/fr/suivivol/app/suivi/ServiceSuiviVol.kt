package fr.suivivol.app.suivi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import fr.suivivol.app.MainActivity
import fr.suivivol.app.R
import fr.suivivol.app.data.Graphe
import fr.suivivol.app.data.Reglages
import fr.suivivol.app.data.db.PointTraceEntity
import fr.suivivol.core.Barometre
import fr.suivivol.core.Coordonnee
import fr.suivivol.core.DetecteurImmobilite
import fr.suivivol.core.DureeRestante
import fr.suivivol.core.Estimation
import fr.suivivol.core.Geo
import fr.suivivol.core.LisseurVitesse
import fr.suivivol.core.PlanDeVol
import fr.suivivol.core.RecalageBarometrique
import fr.suivivol.core.Unites
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service au premier plan (type « location ») qui reçoit les positions GNSS et la pression
 * atmosphérique, calcule les valeurs de vol et enregistre la trace (cahier des charges §8).
 */
class ServiceSuiviVol : Service(), LocationListener, SensorEventListener {

    companion object {
        const val CANAL = "suivi_vol"
        const val ID_NOTIFICATION = 1
        const val ACTION_DEMARRER = "fr.suivivol.app.action.DEMARRER"
        const val ACTION_ARRETER = "fr.suivivol.app.action.ARRETER"
        const val EXTRA_VOL_ID = "volId"
        private const val TAG = "ServiceSuiviVol"
        private const val INTERVALLE_NOTIFICATION_MS = 5_000L
        private const val ESPACEMENT_TRACE_M = 500.0
        private const val TRACE_MAX_POINTS = 20_000

        fun demarrer(context: Context, volId: Long) {
            val intent = Intent(context, ServiceSuiviVol::class.java)
                .setAction(ACTION_DEMARRER)
                .putExtra(EXTRA_VOL_ID, volId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun arreter(context: Context) {
            val intent = Intent(context, ServiceSuiviVol::class.java).setAction(ACTION_ARRETER)
            context.startService(intent)
        }

        fun aPermissionLocalisation(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var gestionnaireLocalisation: LocationManager
    private var gestionnaireCapteurs: SensorManager? = null
    private var capteurPression: Sensor? = null

    private val lisseur = LisseurVitesse(5)
    private val recalage = RecalageBarometrique()
    private val immobilite = DetecteurImmobilite()

    private var volId: Long = -1
    private var plan: PlanDeVol? = null
    private var reglages = Reglages()
    private var tacheReglages: Job? = null
    private var localisationActive = false
    private var frequenceMsActive = 1_000L

    private var altitudeBaroBruteM: Double? = null
    private var derniereMajBaroMs = 0L
    private var derniereNotificationMs = 0L
    private var dernierPointTrace: Coordonnee? = null
    private val trace = ArrayList<Coordonnee>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        gestionnaireLocalisation = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        gestionnaireCapteurs = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        capteurPression = gestionnaireCapteurs?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ARRETER -> {
                arreterSuivi()
                return START_NOT_STICKY
            }
            ACTION_DEMARRER -> {
                val id = intent?.getLongExtra(EXTRA_VOL_ID, -1L) ?: -1L
                if (id > 0) demarrerSuivi(id) else stopSelf()
            }
            else -> {
                // Redémarrage par le système après un arrêt forcé : on reprend le vol actif s'il existe.
                portee.launch {
                    val actif = Graphe.db.volDao().volActif()
                    withContext(Dispatchers.Main) {
                        if (actif != null) demarrerSuivi(actif.id) else stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun demarrerSuivi(id: Long) {
        if (volId == id && localisationActive) return
        volId = id
        passerAuPremierPlan(construireNotification(getString(R.string.notification_attente), null))
        SuiviVolEtat.mettreAJour { it.copy(volId = id, actif = true, message = null) }

        portee.launch {
            val vol = Graphe.db.volDao().vol(id)
            val depart = vol?.let { Graphe.aeroports.parCode(it.aeroportDepart) }
            val arrivee = vol?.let { Graphe.aeroports.parCode(it.aeroportArrivee) }
            if (vol == null || depart == null || arrivee == null) {
                SuiviVolEtat.mettreAJour { it.copy(message = "Vol introuvable", actif = false) }
                withContext(Dispatchers.Main) { arreterSuivi() }
                return@launch
            }
            val nouveauPlan = PlanDeVol(depart, arrivee, vol.decolleLe)
            plan = nouveauPlan
            val points = Graphe.db.volDao().points(id)
            trace.clear()
            var dernier: Coordonnee? = null
            for (p in points) {
                val c = Coordonnee(p.lat, p.lon)
                if (dernier == null || Geo.distanceM(dernier.lat, dernier.lon, c.lat, c.lon) >= ESPACEMENT_TRACE_M) {
                    trace.add(c)
                    dernier = c
                }
            }
            dernierPointTrace = dernier
            val distanceTotale = nouveauPlan.distanceTheoriqueKm
            SuiviVolEtat.mettreAJour {
                it.copy(
                    plan = nouveauPlan,
                    nbPoints = points.size,
                    trace = trace.toList(),
                    distanceRestanteKm = it.distanceRestanteKm ?: distanceTotale,
                    barometreDisponible = capteurPression != null,
                )
            }
            withContext(Dispatchers.Main) {
                demarrerCapteurs()
                ecouterReglages()
            }
        }
    }

    private fun ecouterReglages() {
        tacheReglages?.cancel()
        tacheReglages = portee.launch {
            Graphe.reglages.flux.collectLatest { r ->
                reglages = r
                val frequence = r.frequenceSecondes.coerceIn(1, 60) * 1_000L
                if (frequence != frequenceMsActive) {
                    frequenceMsActive = frequence
                    withContext(Dispatchers.Main) { demarrerLocalisation() }
                }
            }
        }
    }

    private fun demarrerCapteurs() {
        demarrerLocalisation()
        capteurPression?.let { capteur ->
            gestionnaireCapteurs?.registerListener(this, capteur, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    @SuppressLint("MissingPermission")
    private fun demarrerLocalisation() {
        if (!aPermissionLocalisation(this)) {
            SuiviVolEtat.mettreAJour { it.copy(permissionRefusee = true, message = "Permission de localisation refusée") }
            return
        }
        try {
            if (localisationActive) gestionnaireLocalisation.removeUpdates(this)
            val gpsActive = gestionnaireLocalisation.isProviderEnabled(LocationManager.GPS_PROVIDER)
            SuiviVolEtat.mettreAJour { it.copy(gpsActive = gpsActive, permissionRefusee = false) }
            gestionnaireLocalisation.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                frequenceMsActive,
                0f,
                this,
                Looper.getMainLooper(),
            )
            localisationActive = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Localisation refusée", e)
            SuiviVolEtat.mettreAJour { it.copy(permissionRefusee = true, message = "Permission de localisation refusée") }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Fournisseur GPS indisponible", e)
            SuiviVolEtat.mettreAJour { it.copy(gpsActive = false, message = "GPS indisponible sur cet appareil") }
        }
    }

    private fun passerAuPremierPlan(notification: Notification) {
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, ID_NOTIFICATION, notification, type)
        } catch (e: Exception) {
            // Sur Android 14+, démarrer un service « location » sans la permission lève une exception.
            Log.e(TAG, "Impossible de passer au premier plan", e)
            SuiviVolEtat.mettreAJour { it.copy(permissionRefusee = true, actif = false, message = "Permission de localisation nécessaire") }
            stopSelf()
        }
    }

    // ----- Localisation -----

    override fun onLocationChanged(location: Location) {
        val maintenant = System.currentTimeMillis()
        val planCourant = plan
        val vitesseKmh = if (location.hasSpeed()) location.speed * 3.6 else null
        val altitudeGps = if (location.hasAltitude()) location.altitude else null
        val precision = if (location.hasAccuracy()) location.accuracy.toDouble() else null
        val cap = if (location.hasBearing()) location.bearing.toDouble() else null

        lisseur.ajouter(vitesseKmh)
        immobilite.signaler(maintenant, vitesseKmh)

        val baroBrute = altitudeBaroBruteM
        if (altitudeGps != null && baroBrute != null && gpsFiable(location)) {
            recalage.ajouterMesure(altitudeGps, baroBrute)
        }
        val altitudeBaro = baroBrute?.let { recalage.altitudeAffichee(it) }
        val vitesseLissee = lisseur.vitesseLissee
        val position = Coordonnee(location.latitude, location.longitude)

        val distanceRestante: Double? = planCourant?.let { Geo.distanceKm(position, it.arrivee.coordonnee) }
        val progression: Double = if (planCourant != null && distanceRestante != null) {
            Estimation.progression(distanceRestante, planCourant.distanceTheoriqueKm)
        } else {
            0.0
        }
        val duree: DureeRestante = when {
            distanceRestante == null -> DureeRestante.Inconnue
            immobilite.estImmobile(maintenant) -> DureeRestante.EnAttenteDeMouvement
            else -> DureeRestante.Estimee(Estimation.dureeRestante(distanceRestante, vitesseLissee))
        }
        val heureArrivee: Long? = (duree as? DureeRestante.Estimee)?.let { maintenant + it.duree.inWholeMilliseconds }

        val dernier = dernierPointTrace
        if (dernier == null || Geo.distanceM(dernier.lat, dernier.lon, position.lat, position.lon) >= ESPACEMENT_TRACE_M) {
            trace.add(position)
            if (trace.size > TRACE_MAX_POINTS) trace.removeAt(0)
            dernierPointTrace = position
        }
        val traceCopie = trace.toList()

        SuiviVolEtat.mettreAJour {
            it.copy(
                position = position,
                altitudeGpsM = altitudeGps,
                altitudeBaroM = altitudeBaro,
                vitesseLisseeKmh = vitesseLissee,
                vitesseInstantaneeKmh = vitesseKmh,
                capDeg = cap,
                precisionM = precision,
                derniereFixMs = maintenant,
                distanceRestanteKm = distanceRestante,
                dureeRestante = duree,
                heureArriveeMs = heureArrivee,
                progression = progression,
                nbPoints = it.nbPoints + 1,
                trace = traceCopie,
                gpsActive = true,
                message = null,
            )
        }

        val idVol = volId
        if (idVol > 0) {
            portee.launch {
                Graphe.db.volDao().insererPoint(
                    PointTraceEntity(
                        volId = idVol,
                        horodatage = maintenant,
                        lat = position.lat,
                        lon = position.lon,
                        altitudeGpsM = altitudeGps,
                        altitudeBaroM = altitudeBaro,
                        vitesseSolKmh = vitesseKmh,
                        capDeg = cap,
                        precisionM = precision,
                    ),
                )
            }
        }

        if (maintenant - derniereNotificationMs >= INTERVALLE_NOTIFICATION_MS) {
            derniereNotificationMs = maintenant
            val altitude = altitudeBaro ?: altitudeGps
            val texte = listOfNotNull(
                altitude?.let { Unites.formatAltitude(it, reglages.uniteAltitude) },
                vitesseLissee?.let { Unites.formatVitesse(it, reglages.uniteVitesse) },
                distanceRestante?.let { "reste ${Unites.formatDistanceKm(it)}" },
            ).joinToString(" · ")
            mettreAJourNotification(construireNotification(texte.ifBlank { getString(R.string.notification_attente) }, planCourant))
        }
    }

    private fun gpsFiable(location: Location): Boolean {
        if (!location.hasAccuracy() || location.accuracy > 30f) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
            return location.verticalAccuracyMeters <= 50f
        }
        return true
    }

    // Méthode obsolète mais encore appelée par le système avant l'API 29 : à conserver.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

    override fun onProviderEnabled(provider: String) {
        SuiviVolEtat.mettreAJour { it.copy(gpsActive = true, message = null) }
    }

    override fun onProviderDisabled(provider: String) {
        SuiviVolEtat.mettreAJour { it.copy(gpsActive = false, message = "Localisation désactivée : activez-la dans les réglages du téléphone") }
    }

    // ----- Baromètre -----

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PRESSURE) return
        val pression = event.values.firstOrNull()?.toDouble() ?: return
        if (pression <= 0.0 || pression.isNaN()) return
        val brute = Barometre.altitudeM(pression)
        altitudeBaroBruteM = brute
        val maintenant = System.currentTimeMillis()
        if (maintenant - derniereMajBaroMs >= 1_000L) {
            derniereMajBaroMs = maintenant
            val affichee = recalage.altitudeAffichee(brute)
            SuiviVolEtat.mettreAJour { it.copy(altitudeBaroM = affichee, barometreDisponible = true) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ----- Notification -----

    private fun construireNotification(texte: String, planCourant: PlanDeVol?): Notification {
        val intention = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val enAttente = PendingIntent.getActivity(
            this,
            0,
            intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val titre = planCourant?.let { getString(R.string.notification_titre, it.libelle) } ?: getString(R.string.app_name)
        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titre)
            .setContentText(texte)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(enAttente)
            .build()
    }

    private fun mettreAJourNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(ID_NOTIFICATION, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification refusée", e)
        }
    }

    // ----- Arrêt -----

    private fun arreterSuivi() {
        tacheReglages?.cancel()
        if (localisationActive) {
            try {
                gestionnaireLocalisation.removeUpdates(this)
            } catch (_: SecurityException) {
            }
            localisationActive = false
        }
        gestionnaireCapteurs?.unregisterListener(this)
        SuiviVolEtat.mettreAJour { it.copy(actif = false) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        arreterSuivi()
        portee.cancel()
        super.onDestroy()
    }
}
