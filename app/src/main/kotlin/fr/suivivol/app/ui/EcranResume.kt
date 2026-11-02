package fr.suivivol.app.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.suivivol.app.carte.CarteVol
import fr.suivivol.app.data.Graphe
import fr.suivivol.app.data.Reglages
import fr.suivivol.app.data.db.VolEntity
import fr.suivivol.app.export.ExportGpx
import fr.suivivol.core.Aeroport
import fr.suivivol.core.AnalyseTrace
import fr.suivivol.core.Coordonnee
import fr.suivivol.core.Estimation
import fr.suivivol.core.PlanDeVol
import fr.suivivol.core.PointTrace
import fr.suivivol.core.StatistiquesVol
import fr.suivivol.core.Unites
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private data class DonneesResume(
    val vol: VolEntity,
    val depart: Aeroport?,
    val arrivee: Aeroport?,
    val points: List<PointTrace>,
    val stats: StatistiquesVol,
) {
    val plan: PlanDeVol? get() = if (depart != null && arrivee != null) PlanDeVol(depart, arrivee, vol.decolleLe) else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranResume(volId: Long, onNouveauVol: () -> Unit, onRetour: () -> Unit) {
    val reglages by Graphe.reglages.flux.collectAsStateWithLifecycle(initialValue = Reglages())
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    var donnees by remember { mutableStateOf<DonneesResume?>(null) }
    var introuvable by remember { mutableStateOf(false) }
    var confirmerSuppression by remember { mutableStateOf(false) }

    LaunchedEffect(volId) {
        val dao = Graphe.db.volDao()
        val vol = dao.vol(volId)
        if (vol == null) {
            introuvable = true
            return@LaunchedEffect
        }
        val depart = Graphe.aeroports.parCode(vol.aeroportDepart)
        val arrivee = Graphe.aeroports.parCode(vol.aeroportArrivee)
        val points = dao.points(volId).map { it.versCore() }
        val stats = AnalyseTrace.statistiques(points, depart?.coordonnee, arrivee?.coordonnee)
        donnees = DonneesResume(vol, depart, arrivee, points, stats)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Résumé du vol") },
                navigationIcon = {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { marges ->
        val d = donnees
        when {
            introuvable -> Column(modifier = Modifier.padding(marges).padding(16.dp)) {
                CarteMessage("Ce vol n'existe plus.", erreur = true)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onNouveauVol) { Text("Nouveau vol") }
            }
            d == null -> Column(
                modifier = Modifier.padding(marges).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            else -> Column(
                modifier = Modifier
                    .padding(marges)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val vol = d.vol
                val stats = d.stats
                val dureeReelle = vol.termineLe?.let { (it - vol.decolleLe).coerceAtLeast(0).milliseconds }
                Spacer(Modifier.height(4.dp))
                Text("${vol.aeroportDepart} → ${vol.aeroportArrivee}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(d.depart?.nom, d.arrivee?.nom).joinToString(" → "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                d.plan?.let { plan ->
                    CarteVol(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        plan = plan,
                        position = null,
                        capDeg = null,
                        trace = d.points.map { Coordonnee(it.lat, it.lon) }.let { pts -> if (pts.size > 4000) pts.filterIndexed { i, _ -> i % (pts.size / 4000 + 1) == 0 } else pts },
                        horsLigne = true,
                        suivrePosition = false,
                        interactif = false,
                    )
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Horaires", style = MaterialTheme.typography.titleMedium)
                        Ligne("Décollage", formatDateHeure(vol.decolleLe))
                        Ligne("Arrivée", formatDateHeure(vol.termineLe))
                        Ligne("Durée réelle", dureeReelle?.let { Estimation.formatHMin(it) } ?: "vol non terminé")
                        HorizontalDivider()
                        Text("Distances", style = MaterialTheme.typography.titleMedium)
                        Ligne("Distance parcourue (GPS)", Unites.formatDistanceKm(stats.distanceParcourueKm))
                        Ligne("Distance théorique (grand-cercle)", Unites.formatDistanceKm(vol.distanceTheoriqueKm))
                        val ecart = stats.distanceParcourueKm - vol.distanceTheoriqueKm
                        Ligne(
                            "Écart",
                            String.format(Locale.FRANCE, "%+.0f km (%+.1f %%)", ecart, if (vol.distanceTheoriqueKm > 0) ecart / vol.distanceTheoriqueKm * 100 else 0.0),
                        )
                        HorizontalDivider()
                        Text("Altitude et vitesse", style = MaterialTheme.typography.titleMedium)
                        Ligne("Altitude maximale", Unites.formatAltitude(stats.altitudeMaxM, reglages.uniteAltitude))
                        Ligne("Vitesse maximale", Unites.formatVitesse(stats.vitesseMaxKmh, reglages.uniteVitesse))
                        Ligne("Vitesse moyenne", Unites.formatVitesse(stats.vitesseMoyenneKmh, reglages.uniteVitesse))
                        HorizontalDivider()
                        Text("Trace", style = MaterialTheme.typography.titleMedium)
                        Ligne("Points enregistrés", stats.nbPoints.toString())
                        Ligne("Écart maximal à la route théorique", Unites.formatDistanceKm(stats.ecartMaxRouteKm))
                        Ligne("Durée d'enregistrement", Estimation.formatHMin(stats.dureeEnregistree))
                    }
                }

                Text(
                    "La distance parcourue est normalement supérieure à la distance grand-cercle : un avion ne vole pas en ligne droite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = {
                        portee.launch {
                            ExportGpx.partager(contexte, vol.aeroportDepart, vol.aeroportArrivee, vol.decolleLe, d.points, d.depart, d.arrivee)
                        }
                    },
                    enabled = d.points.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exporter la trace GPX")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onNouveauVol, modifier = Modifier.weight(1f)) { Text("Nouveau vol") }
                    OutlinedButton(onClick = { confirmerSuppression = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Effacer ce vol")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmerSuppression) {
        AlertDialog(
            onDismissRequest = { confirmerSuppression = false },
            title = { Text("Effacer ce vol ?") },
            text = { Text("Le vol et sa trace GPS seront supprimés définitivement du téléphone.") },
            confirmButton = {
                Button(onClick = {
                    confirmerSuppression = false
                    portee.launch {
                        Graphe.db.volDao().supprimerVol(volId)
                        onNouveauVol()
                    }
                }) { Text("Effacer") }
            },
            dismissButton = { TextButton(onClick = { confirmerSuppression = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Ligne(etiquette: String, valeur: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiquette, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(valeur, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
