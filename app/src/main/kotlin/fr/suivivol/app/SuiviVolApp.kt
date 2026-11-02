package fr.suivivol.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import fr.suivivol.app.carte.TuilesOsm
import fr.suivivol.app.data.Graphe
import fr.suivivol.app.suivi.ServiceSuiviVol

class SuiviVolApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graphe.initialiser(this)
        TuilesOsm.configurer(this)
        creerCanalNotification()
    }

    private fun creerCanalNotification() {
        val gestionnaire = getSystemService(NotificationManager::class.java)
        val canal = NotificationChannel(
            ServiceSuiviVol.CANAL,
            getString(R.string.canal_notification),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Notification persistante pendant le suivi d'un vol"
            setShowBadge(false)
        }
        gestionnaire.createNotificationChannel(canal)
    }
}
