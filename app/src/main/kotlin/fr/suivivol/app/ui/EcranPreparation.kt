package fr.suivivol.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.suivivol.app.carte.CarteVol
import fr.suivivol.app.carte.TelechargementCorridor
import fr.suivivol.app.data.Graphe
import fr.suivivol.app.data.Reglages
import fr.suivivol.core.Aeroport
import fr.suivivol.core.Estimation
import fr.suivivol.core.Geo
import fr.suivivol.core.Unites
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranPreparation(
    onDecolle: (Long) -> Unit,
    onOuvrirReglages: () -> Unit,
    onOuvrirResume: (Long) -> Unit,
    vm: PreparationViewModel = viewModel(),
) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val historique by vm.historique.collectAsStateWithLifecycle()
    val reglages by Graphe.reglages.flux.collectAsStateWithLifecycle(initialValue = Reglages())
    val corridor by TelechargementCorridor.etat.collectAsStateWithLifecycle()
    val contexte = LocalContext.current
    var afficherHeure by remember { mutableStateOf(false) }

    val lanceurPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultats ->
        if (resultats[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            vm.decoller(onDecolle)
        } else {
            vm.signalerErreur(
                "Sans la localisation précise, l'application ne peut rien afficher : le GPS est sa seule source de données. " +
                    "Autorisez « Position » (précise) dans les paramètres Android de l'application.",
            )
        }
    }

    fun demanderDecollage() {
        val permissionOk = ContextCompat.checkSelfPermission(contexte, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (permissionOk) {
            vm.decoller(onDecolle)
        } else {
            val demandes = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) demandes.add(Manifest.permission.POST_NOTIFICATIONS)
            lanceurPermissions.launch(demandes.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Préparation du vol") },
                actions = {
                    IconButton(onClick = onOuvrirReglages) { Icon(Icons.Default.Settings, contentDescription = "Réglages") }
                },
            )
        },
    ) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            ChampAeroport(
                etiquette = "Aéroport de départ",
                requete = etat.requeteDepart,
                selection = etat.depart,
                resultats = etat.resultatsDepart,
                onSaisie = vm::saisirDepart,
                onChoix = vm::choisirDepart,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = vm::inverser) { Text("⇅  Inverser départ et arrivée") }
            }
            ChampAeroport(
                etiquette = "Aéroport d'arrivée",
                requete = etat.requeteArrivee,
                selection = etat.arrivee,
                resultats = etat.resultatsArrivee,
                onSaisie = vm::saisirArrivee,
                onChoix = vm::choisirArrivee,
            )
            if (etat.memeAeroport) {
                CarteMessage("Le départ et l'arrivée sont le même aéroport : choisissez deux aéroports différents.", erreur = true)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { afficherHeure = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Heure de décollage", style = MaterialTheme.typography.labelMedium)
                        Text(formatDateHeure(etat.decolleLeMs), style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { vm.definirHeureDecollage(System.currentTimeMillis()) }) { Text("Maintenant") }
                }
            }

            val plan = etat.plan
            if (plan != null) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(plan.libelle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${plan.depart.nom} → ${plan.arrivee.nom}", style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        LigneInfo("Distance grand-cercle", Unites.formatDistanceKm(plan.distanceTheoriqueKm))
                        LigneInfo(
                            "Durée théorique à ${reglages.vitesseCroisiereKmh} km/h",
                            Estimation.formatHMin(plan.dureeTheorique(reglages.vitesseCroisiereKmh.toDouble())),
                        )
                        LigneInfo("Cap initial", Geo.formatCap(plan.capInitialDeg))
                        LigneInfo(
                            "Altitude des terrains",
                            "${Unites.formatAltitude(plan.depart.altitudeM, reglages.uniteAltitude)} → ${Unites.formatAltitude(plan.arrivee.altitudeM, reglages.uniteAltitude)}",
                        )
                    }
                }

                CarteVol(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    plan = plan,
                    position = null,
                    capDeg = null,
                    trace = emptyList(),
                    horsLigne = false,
                    suivrePosition = false,
                    interactif = false,
                )

                CarteCorridor(corridor = corridor, estimation = remember(plan) { TelechargementCorridor.estimer(plan) }) {
                    TelechargementCorridor.lancer(contexte, plan)
                }
            } else {
                CarteMessage(
                    "Saisissez un code IATA (CDG), OACI (LFPG) ou un nom de ville (Tokyo). " +
                        "La base d'aéroports est embarquée : la recherche fonctionne hors ligne.",
                )
            }

            etat.erreur?.let { CarteMessage(it, erreur = true) }

            Button(
                onClick = { demanderDecollage() },
                enabled = plan != null && !etat.demarrageEnCours,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Décoller", style = MaterialTheme.typography.titleMedium)
            }

            Text(
                "Usage informatif uniquement : cette application n'est pas un instrument de navigation aéronautique. " +
                    "Le GPS fonctionne en mode avion ; placez-vous côté hublot pour une bonne réception.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val termines = historique.filter { it.termineLe != null }
            if (termines.isNotEmpty()) {
                Text("Vols précédents", style = MaterialTheme.typography.titleMedium)
                for (vol in termines.take(10)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOuvrirResume(vol.id) },
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${vol.aeroportDepart} → ${vol.aeroportArrivee}", fontWeight = FontWeight.SemiBold)
                                Text(formatDateHeure(vol.decolleLe), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(Unites.formatDistanceKm(vol.distanceTheoriqueKm), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (afficherHeure) {
        val fuseau = ZoneId.systemDefault()
        val actuelle = LocalDateTime.ofInstant(Instant.ofEpochMilli(etat.decolleLeMs), fuseau)
        val etatHeure = rememberTimePickerState(initialHour = actuelle.hour, initialMinute = actuelle.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { afficherHeure = false },
            title = { Text("Heure de décollage") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = etatHeure)
                    Text(
                        "Si l'heure choisie est dans le futur de plus de 12 h, elle est comptée pour hier.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val maintenant = LocalDateTime.now(fuseau)
                    var choisi = LocalDateTime.of(LocalDate.now(fuseau), LocalTime.of(etatHeure.hour, etatHeure.minute))
                    if (choisi.isAfter(maintenant.plusHours(12))) choisi = choisi.minusDays(1)
                    vm.definirHeureDecollage(choisi.atZone(fuseau).toInstant().toEpochMilli())
                    afficherHeure = false
                }) { Icon(Icons.Default.Check, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { afficherHeure = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun LigneInfo(etiquette: String, valeur: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiquette, style = MaterialTheme.typography.bodyMedium)
        Text(valeur, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChampAeroport(
    etiquette: String,
    requete: String,
    selection: Aeroport?,
    resultats: List<Aeroport>,
    onSaisie: (String) -> Unit,
    onChoix: (Aeroport) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = requete,
            onValueChange = onSaisie,
            label = { Text(etiquette) },
            placeholder = { Text("CDG, LFPG ou Paris") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            supportingText = if (selection != null) {
                { Text(selection.libelle(Locale.getDefault())) }
            } else null,
            trailingIcon = if (requete.isNotEmpty()) {
                { IconButton(onClick = { onSaisie("") }) { Icon(Icons.Default.Clear, contentDescription = "Effacer") } }
            } else null,
        )
        if (selection == null && resultats.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    for ((index, a) in resultats.withIndex()) {
                        if (index > 0) HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChoix(a) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(a.libelle(Locale.getDefault()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            val sous = a.sousLibelle()
                            if (sous.isNotEmpty()) Text(sous, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteCorridor(
    corridor: TelechargementCorridor.Etat,
    estimation: TelechargementCorridor.Estimation,
    onLancer: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Carte hors ligne", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sans ce téléchargement, la carte sera vide en vol. À faire avant le décollage, avec le wifi. " +
                    "Corridor de 250 km autour du trajet, zooms 3 à 8 : environ ${estimation.nbTuiles} tuiles, " +
                    "soit ≈ ${formatMo(estimation.octets)} (estimation à 15 Ko par tuile).",
                style = MaterialTheme.typography.bodySmall,
            )
            when (corridor) {
                is TelechargementCorridor.Etat.EnCours -> {
                    val fraction = if (corridor.total > 0) corridor.fait.toFloat() / corridor.total else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${corridor.fait} / ${corridor.total} tuiles", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { TelechargementCorridor.annuler() }) { Text("Annuler") }
                    }
                }
                is TelechargementCorridor.Etat.Termine -> {
                    val texte = if (corridor.erreurs == 0) {
                        "Carte du trajet téléchargée (${corridor.total} tuiles)."
                    } else {
                        "Téléchargement terminé avec ${corridor.erreurs} tuile(s) manquante(s) sur ${corridor.total} : vérifiez la connexion et relancez."
                    }
                    Text(texte, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    OutlinedButton(onClick = onLancer) { Text("Télécharger à nouveau") }
                }
                is TelechargementCorridor.Etat.Echec -> {
                    CarteMessage("Échec : ${corridor.message}", erreur = true)
                    OutlinedButton(onClick = onLancer) { Text("Réessayer") }
                }
                TelechargementCorridor.Etat.Inactif -> {
                    Button(onClick = onLancer, modifier = Modifier.fillMaxWidth()) { Text("Télécharger la carte du trajet") }
                }
            }
        }
    }
}

fun formatMo(octets: Long): String = String.format(Locale.FRANCE, "%.1f Mo", octets / 1_048_576.0)
