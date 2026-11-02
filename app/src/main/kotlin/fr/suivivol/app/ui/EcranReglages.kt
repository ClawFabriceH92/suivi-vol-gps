package fr.suivivol.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.suivivol.app.carte.TuilesOsm
import fr.suivivol.app.data.Graphe
import fr.suivivol.app.data.Reglages
import fr.suivivol.app.data.ThemeApp
import fr.suivivol.app.maj.VerificateurMaj
import fr.suivivol.core.UniteAltitude
import fr.suivivol.core.UniteVitesse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranReglages(onRetour: () -> Unit, onOuvrirAPropos: () -> Unit) {
    val reglages by Graphe.reglages.flux.collectAsStateWithLifecycle(initialValue = Reglages())
    val portee = rememberCoroutineScope()
    val contexte = LocalContext.current
    val depot = Graphe.reglages
    var tailleCache by remember { mutableLongStateOf(-1L) }
    var confirmerCache by remember { mutableStateOf(false) }
    var resultatMaj by remember { mutableStateOf<VerificateurMaj.Resultat?>(null) }
    var verificationEnCours by remember { mutableStateOf(false) }

    suspend fun rafraichirCache() {
        tailleCache = withContext(Dispatchers.IO) { TuilesOsm.tailleCacheOctets() }
    }
    LaunchedEffect(Unit) { rafraichirCache() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Section("Vitesse de croisière de référence") {
                var curseur by remember(reglages.vitesseCroisiereKmh) { mutableFloatStateOf(reglages.vitesseCroisiereKmh.toFloat()) }
                Text("${curseur.toInt()} km/h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Slider(
                    value = curseur,
                    onValueChange = { curseur = (it / 10f).toInt() * 10f },
                    onValueChangeFinished = { portee.launch { depot.definirVitesseCroisiere(curseur.toInt()) } },
                    valueRange = 300f..1000f,
                )
                Text("Sert à la durée théorique affichée avant le décollage (850 km/h par défaut).", style = MaterialTheme.typography.bodySmall)
            }

            Section("Unités") {
                ChoixSegmente(
                    options = listOf("Mètres" to UniteAltitude.METRES, "Pieds" to UniteAltitude.PIEDS),
                    selection = reglages.uniteAltitude,
                ) { portee.launch { depot.definirUniteAltitude(it) } }
                ChoixSegmente(
                    options = listOf("km/h" to UniteVitesse.KMH, "Nœuds" to UniteVitesse.NOEUDS),
                    selection = reglages.uniteVitesse,
                ) { portee.launch { depot.definirUniteVitesse(it) } }
            }

            Section("Fréquence de localisation") {
                ChoixSegmente(
                    options = Reglages.FREQUENCES_SECONDES.map { "$it s" to it },
                    selection = reglages.frequenceSecondes,
                ) { portee.launch { depot.definirFrequence(it) } }
                Text(
                    "1 s au décollage et à l'atterrissage ; 15 s suffit en croisière et économise la batterie (≈ 5 à 8 %/h à 1 s).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Section("Écran") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Garder l'écran allumé pendant le vol", modifier = Modifier.weight(1f))
                    Switch(checked = reglages.garderEcranAllume, onCheckedChange = { portee.launch { depot.definirGarderEcran(it) } })
                }
                ChoixSegmente(
                    options = listOf("Système" to ThemeApp.SYSTEME, "Clair" to ThemeApp.CLAIR, "Sombre" to ThemeApp.SOMBRE),
                    selection = reglages.theme,
                ) { portee.launch { depot.definirTheme(it) } }
            }

            Section("Cache de carte") {
                Text(
                    if (tailleCache < 0) "Taille : calcul…" else "Taille actuelle : ${formatMo(tailleCache)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { confirmerCache = true }) { Text("Vider le cache des tuiles") }
            }

            Section("Mise à jour") {
                Text(
                    "Version ${fr.suivivol.app.BuildConfig.VERSION_NAME} (build ${fr.suivivol.app.BuildConfig.DATE_BUILD}). " +
                        "La vérification interroge GitHub : elle nécessite du réseau et n'est jamais automatique.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        verificationEnCours = true
                        portee.launch {
                            resultatMaj = VerificateurMaj.verifier()
                            verificationEnCours = false
                        }
                    },
                    enabled = !verificationEnCours,
                ) { Text(if (verificationEnCours) "Vérification…" else "Vérifier les mises à jour") }
                when (val r = resultatMaj) {
                    null -> Unit
                    VerificateurMaj.Resultat.AJour -> Text("L'application est à jour.", fontWeight = FontWeight.Medium)
                    is VerificateurMaj.Resultat.Erreur -> Text("Vérification impossible : ${r.message}", color = MaterialTheme.colorScheme.error)
                    is VerificateurMaj.Resultat.Disponible -> {
                        Text("Nouvelle version ${r.versionName} disponible.", fontWeight = FontWeight.Medium)
                        r.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Button(onClick = {
                            runCatching { contexte.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.urlApk))) }
                        }) { Text("Télécharger l'APK") }
                    }
                }
            }

            TextButton(onClick = onOuvrirAPropos) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.height(4.dp))
                Text("À propos, limites et sources")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmerCache) {
        AlertDialog(
            onDismissRequest = { confirmerCache = false },
            title = { Text("Vider le cache ?") },
            text = { Text("Toutes les tuiles de carte téléchargées seront supprimées. Il faudra retélécharger la carte avant le prochain vol.") },
            confirmButton = {
                Button(onClick = {
                    confirmerCache = false
                    portee.launch {
                        withContext(Dispatchers.IO) { TuilesOsm.viderCache() }
                        rafraichirCache()
                    }
                }) { Text("Vider") }
            },
            dismissButton = { TextButton(onClick = { confirmerCache = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Section(titre: String, contenu: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titre, style = MaterialTheme.typography.titleMedium)
            contenu()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoixSegmente(options: List<Pair<String, T>>, selection: T, onChoix: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (libelle, valeur) ->
            SegmentedButton(
                selected = valeur == selection,
                onClick = { onChoix(valeur) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(libelle) }
        }
    }
}
