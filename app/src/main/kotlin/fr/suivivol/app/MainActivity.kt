package fr.suivivol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.suivivol.app.data.Graphe
import fr.suivivol.app.ui.AppSuiviVol
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Lecture synchrone des réglages au démarrage (quelques millisecondes) pour ouvrir
        // directement l'écran de vol si un vol est en cours.
        val volActifId = runBlocking { Graphe.reglages.actuels().volActifId }
        setContent {
            AppSuiviVol(volActifInitial = volActifId)
        }
    }
}
