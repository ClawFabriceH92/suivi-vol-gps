package com.trucdecomptable.suivivol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trucdecomptable.suivivol.ui.EcranArrivee
import com.trucdecomptable.suivivol.ui.EcranPreparation
import com.trucdecomptable.suivivol.ui.EcranSuiviVol
import com.trucdecomptable.suivivol.ui.VolViewModel
import org.osmdroid.config.Configuration

/** Écrans de l'application, encodés en Int pour rester sauvegardables dans le Bundle. */
private const val ECRAN_PREPARATION = 0
private const val ECRAN_VOL = 1
private const val ECRAN_ARRIVEE = 2

/**
 * Point d'entrée : initialise osmdroid (user-agent non vide obligatoire pour les
 * serveurs de tuiles), puis affiche les trois écrans pilotés par le [VolViewModel]
 * sans navigation lourde (un simple état local).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid doit être configuré avant la création de toute MapView : on
        // charge les préférences et on force un user-agent non vide (exigé par
        // les serveurs de tuiles, sinon aucune tuile n'est servie).
        val preferences = getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        Configuration.getInstance().load(this, preferences)
        if (Configuration.getInstance().userAgentValue.isNullOrBlank()) {
            Configuration.getInstance().userAgentValue = packageName
        }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Racine()
                }
            }
        }
    }
}

@Composable
private fun Racine() {
    val contexte = LocalContext.current
    // La clé de session force la création d'un nouveau VolViewModel quand on
    // l'incrémente : c'est le mécanisme « remettre l'état à zéro » (nouveau vol
    // ou effacement du choix), sans avoir à ajouter de méthode au ViewModel.
    var cleSession by rememberSaveable { mutableIntStateOf(0) }
    val vueModele: VolViewModel = viewModel(key = "suivi-vol-$cleSession")
    val ecran by vueModele.ecran.collectAsStateWithLifecycle()

    // Écran courant : un simple état local suffit, pas de bibliothèque de nav.
    var ecranApp by rememberSaveable { mutableIntStateOf(ECRAN_PREPARATION) }

    // Permission de localisation demandée au premier lancement ; un refus est
    // signalé par une bannière et le suivi reste inactif (le ViewModel gère
    // aussi ce cas en posant un message au lieu de planter).
    var permissionRefusee by rememberSaveable { mutableStateOf(false) }
    val lanceurPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { accordee -> permissionRefusee = !accordee }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(contexte, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            lanceurPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissionRefusee = false
        }
    }

    // Transitions pilotées par l'état de vol : préparation → vol → arrivée.
    LaunchedEffect(ecran.enVol) {
        when {
            ecran.enVol -> ecranApp = ECRAN_VOL
            !ecran.enVol && ecranApp == ECRAN_VOL -> ecranApp = ECRAN_ARRIVEE
        }
    }

    when (ecranApp) {
        ECRAN_PREPARATION -> EcranPreparation(
            ecran = ecran,
            permissionRefusee = permissionRefusee,
            rechercher = vueModele::rechercher,
            choisirDepart = vueModele::choisirDepart,
            choisirArrivee = vueModele::choisirArrivee,
            corrigerArrivee = vueModele::corrigerArrivee,
            demarrerSuivi = vueModele::demarrerSuivi,
            effacerChoix = { cleSession++ },
        )
        ECRAN_VOL -> EcranSuiviVol(
            ecran = ecran,
            rechercher = vueModele::rechercher,
            corrigerArrivee = vueModele::corrigerArrivee,
            arreterSuivi = vueModele::arreterSuivi,
        )
        ECRAN_ARRIVEE -> EcranArrivee(
            ecran = ecran,
            nouveauVol = {
                ecranApp = ECRAN_PREPARATION
                cleSession++
            },
        )
    }
}
