package fr.suivivol.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.suivivol.app.carte.CarteVol
import fr.suivivol.app.data.Graphe
import fr.suivivol.app.data.Reglages
import fr.suivivol.app.suivi.ServiceSuiviVol
import fr.suivivol.app.suivi.SuiviVolEtat
import fr.suivivol.core.DureeRestante
import fr.suivivol.core.Estimation
import fr.suivivol.core.Geo
import fr.suivivol.core.Unites
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranVol(
    onVolTermine: (Long) -> Unit,
    onOuvrirReglages: () -> Unit,
    onRetourPreparation: () -> Unit,
) {
    val etat by SuiviVolEtat.etat.collectAsStateWithLifecycle()
    val reglages by Graphe.reglages.flux.collectAsStateWithLifecycle(initialValue = Reglages())
    val contexte = LocalContext.current
    val vue = LocalView.current
    val portee = rememberCoroutineScope()
    var confirmerFin by remember { mutableStateOf(false) }
    var suivrePosition by remember { mutableStateOf(true) }
    var demandeRecentrage by remember { mutableIntStateOf(0) }
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Horloge pour le temps écoulé.
    LaunchedEffect(Unit) {
        while (true) {
            maintenant = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // Si le processus a été relancé, on s'assure que le service suit bien le vol actif.
    LaunchedEffect(reglages.volActifId, etat.actif) {
        val id = reglages.volActifId
        if (id != null && !etat.actif) ServiceSuiviVol.demarrer(contexte, id)
    }

    // Écran allumé pendant le vol (réglage).
    DisposableEffect(reglages.garderEcranAllume) {
        vue.keepScreenOn = reglages.garderEcranAllume
        onDispose { vue.keepScreenOn = false }
    }

    val plan = etat.plan
    val volId = etat.volId ?: reglages.volActifId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan?.libelle ?: "Vol en cours") },
                actions = {
                    IconButton(onClick = onOuvrirReglages) { Icon(Icons.Default.Settings, contentDescription = "Réglages") }
                },
            )
        },
    ) { marges ->
        Column(modifier = Modifier.padding(marges).fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (etat.permissionRefusee) {
                    CarteMessage(
                        "La localisation est refusée : le GPS est la seule source de données de l'application. " +
                            "Autorisez « Position » (précise) dans les paramètres Android, puis revenez ici.",
                        erreur = true,
                    )
                } else if (!etat.gpsActive) {
                    CarteMessage("La localisation du téléphone est désactivée. Activez-la (elle fonctionne en mode avion).", erreur = true)
                } else if (etat.position == null) {
                    CarteMessage("Recherche des satellites… L'accrochage prend quelques minutes ; placez le téléphone côté hublot.")
                }
                etat.message?.takeIf { !etat.permissionRefusee && etat.gpsActive }?.let { CarteMessage(it, erreur = true) }

                val altitudeGps = etat.altitudeGpsM
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TuileValeur(
                        titre = if (etat.altitudeBaroM != null) "Altitude baro" else "Altitude GPS",
                        valeur = Unites.formatAltitude(etat.altitudeAfficheeM, reglages.uniteAltitude),
                        secondaire = when {
                            etat.altitudeBaroM != null && altitudeGps != null -> "GPS ${Unites.formatAltitude(altitudeGps, reglages.uniteAltitude)}"
                            !etat.barometreDisponible -> "pas de baromètre"
                            else -> null
                        },
                        modifier = Modifier.weight(1f),
                        accent = true,
                    )
                    TuileValeur(
                        titre = "Vitesse sol",
                        valeur = Unites.formatVitesse(etat.vitesseLisseeKmh, reglages.uniteVitesse),
                        secondaire = etat.capDeg?.let { "cap ${Geo.formatCap(it)}" },
                        modifier = Modifier.weight(1f),
                        accent = true,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TuileValeur(
                        titre = "Distance restante",
                        valeur = Unites.formatDistanceKm(etat.distanceRestanteKm),
                        secondaire = plan?.let { "sur ${Unites.formatDistanceKm(it.distanceTheoriqueKm)}" },
                        modifier = Modifier.weight(1f),
                    )
                    val duree = etat.dureeRestante
                    TuileValeur(
                        titre = "Durée restante",
                        valeur = when (duree) {
                            is DureeRestante.Estimee -> Estimation.formatHMin(duree.duree)
                            DureeRestante.EnAttenteDeMouvement -> "en attente"
                            DureeRestante.Inconnue -> "—"
                        },
                        secondaire = when (duree) {
                            is DureeRestante.Estimee -> "arrivée vers ${formatHeure(etat.heureArriveeMs)}"
                            DureeRestante.EnAttenteDeMouvement -> "de mouvement"
                            DureeRestante.Inconnue -> null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Column {
                    LinearProgressIndicator(
                        progress = { etat.progression.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(plan?.depart?.code ?: "", style = MaterialTheme.typography.labelMedium)
                        Text("${(etat.progression * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(plan?.arrivee?.code ?: "", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                CarteVol(
                    modifier = Modifier.fillMaxSize(),
                    plan = plan,
                    position = etat.position,
                    capDeg = etat.capDeg,
                    trace = etat.trace,
                    horsLigne = true,
                    suivrePosition = suivrePosition,
                    demandeRecentrage = demandeRecentrage,
                    onInteractionUtilisateur = { suivrePosition = false },
                )
                SmallFloatingActionButton(
                    onClick = {
                        suivrePosition = true
                        demandeRecentrage++
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Recentrer")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Décollage ${formatHeure(plan?.decolleLeMs)}", style = MaterialTheme.typography.bodySmall)
                    val ecoule = plan?.let { (maintenant - it.decolleLeMs).coerceAtLeast(0).milliseconds }
                    Text(
                        "Écoulé ${ecoule?.let { Estimation.formatHMin(it) } ?: "—"} · ${etat.nbPoints} points",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (volId != null) {
                    OutlinedButton(onClick = { confirmerFin = true }) { Text("Terminer le vol") }
                } else {
                    TextButton(onClick = onRetourPreparation) { Text("Aucun vol actif") }
                }
            }
        }
    }

    if (confirmerFin && volId != null) {
        AlertDialog(
            onDismissRequest = { confirmerFin = false },
            title = { Text("Terminer le vol ?") },
            text = { Text("Le suivi GPS s'arrête et le résumé du vol s'affiche.") },
            confirmButton = {
                Button(onClick = {
                    confirmerFin = false
                    portee.launch {
                        terminerVol(volId)
                        onVolTermine(volId)
                    }
                }) { Text("Terminer") }
            },
            dismissButton = { TextButton(onClick = { confirmerFin = false }) { Text("Continuer le vol") } },
        )
    }
}

/** Clôture le vol : horodatage de fin, arrêt du service, plus de vol actif. */
suspend fun terminerVol(volId: Long) {
    val dao = Graphe.db.volDao()
    dao.vol(volId)?.let { vol ->
        if (vol.termineLe == null) dao.mettreAJourVol(vol.copy(termineLe = System.currentTimeMillis()))
    }
    Graphe.reglages.definirVolActif(null)
    ServiceSuiviVol.arreter(Graphe.application)
    SuiviVolEtat.reinitialiser()
}
