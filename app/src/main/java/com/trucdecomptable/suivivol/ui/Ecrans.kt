package com.trucdecomptable.suivivol.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.trucdecomptable.suivivol.data.Aeroport
import com.trucdecomptable.suivivol.data.ModeAffichage
import com.trucdecomptable.suivivol.data.ReglagesVol
import com.trucdecomptable.suivivol.data.SourceTuiles
import com.trucdecomptable.suivivol.data.Geo
import com.trucdecomptable.suivivol.util.JournalPlantage
import com.trucdecomptable.suivivol.vol.Alternance
import com.trucdecomptable.suivivol.vol.JournalGpx
import com.trucdecomptable.suivivol.vol.OrigineVol
import com.trucdecomptable.suivivol.vol.PointVol
import com.trucdecomptable.suivivol.vol.VolSuivi
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Niveaux de zoom téléchargés pour la carte hors ligne (trajet départ/arrivée). */
private const val ZOOM_MIN = 5
private const val ZOOM_MAX = 9

/** Couleur (ARGB) de la ligne du trajet sur la carte. */
private val COULEUR_TRACE = 0xFF1E88E5.toInt()

/**
 * Écran de préparation : choix du départ et de l'arrivée, affichage de la
 * destination devinée (avec sa confiance), téléchargement des tuiles hors ligne
 * et démarrage du suivi. Les champs restent toujours modifiables et effaçables.
 */
@Composable
fun EcranPreparation(
    ecran: EcranVol,
    detection: Detection,
    permissionRefusee: Boolean,
    rechercher: (String) -> List<Aeroport>,
    choisirDepart: (Aeroport) -> Unit,
    choisirArrivee: (Aeroport) -> Unit,
    demarrerSuivi: () -> Unit,
    relancerDetection: () -> Unit,
    onOuvrirParametres: () -> Unit,
) {
    val contexte = LocalContext.current
    var dialogueAeroports by remember { mutableStateOf(false) }
    var etatTuiles by remember { mutableStateOf<EtatTuiles>(EtatTuiles.Repos) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Prêt à décoller", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onOuvrirParametres) {
                Icon(Icons.Filled.Settings, contentDescription = "Paramétrage")
            }
        }
        Text(
            "Tout est détecté automatiquement. Un seul bouton à presser.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        if (permissionRefusee) {
            BandeauAlerte(
                "Localisation refusée : la détection automatique et le suivi GPS restent " +
                    "inactifs. Autorise l'accès à la position dans les réglages."
            )
        }
        ecran.message?.let { BandeauAlerte(it) }
        detection.erreur?.let { if (!permissionRefusee) BandeauAlerte(it) }

        // --- Départ ---
        val departTexte = when {
            ecran.depart != null -> libelleAeroport(ecran.depart)
            detection.enCours -> "Recherche de ta position…"
            else -> "Aucun aéroport à moins de 40 km"
        }
        val departDetail = when {
            detection.distanceKm != null -> "à %.0f km de ta position".format(detection.distanceKm)
            ecran.depart != null -> "choisi à la main"
            else -> null
        }
        CarteDetection(
            titre = "AÉROPORT DE DÉPART",
            valeur = departTexte,
            detail = departDetail,
            pastilleVerte = ecran.depart != null,
        )

        // --- Destination (seulement si la devinette a déjà parlé) ---
        ecran.arrivee?.let { arrivee ->
            CarteDetection(
                titre = "DESTINATION PROBABLE",
                valeur = libelleAeroport(arrivee),
                detail = "%.0f %% de confiance".format(confiance(ecran) * 100),
                pastilleVerte = false,
            )
        }

        // --- Position ---
        if (detection.latitude != null && detection.longitude != null) {
            CarteDetection(
                titre = "POSITION GPS",
                valeur = "%.4f N · %.4f %s".format(
                    detection.latitude,
                    kotlin.math.abs(detection.longitude),
                    if (detection.longitude < 0) "O" else "E",
                ),
                detail = ecran.dernier?.let { "cap %03d°".format(it.capDeg.roundToInt()) },
                pastilleVerte = true,
            )
        }

        Button(
            onClick = demarrerSuivi,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text("DÉMARRER LE SUIVI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { dialogueAeroports = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Corriger les aéroports")
        }

        // --- Option : carte hors ligne, jamais un préalable ---
        Text("Carte hors ligne (option)", style = MaterialTheme.typography.titleSmall)
        Button(
            onClick = {
                val d = ecran.depart
                val a = ecran.arrivee
                if (d != null && a != null) {
                    etatTuiles = EtatTuiles.EnCours(0, 0, ZOOM_MIN)
                    lancerTelechargementTuiles(contexte, d, a) { etatTuiles = it }
                }
            },
            enabled = ecran.depart != null && ecran.arrivee != null && etatTuiles !is EtatTuiles.EnCours,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (ecran.depart == null || ecran.arrivee == null) "Préparer la carte (aéroports requis)"
                else "Préparer la carte"
            )
        }
        when (val etat = etatTuiles) {
            is EtatTuiles.EnCours -> {
                if (etat.total > 0) {
                    LinearProgressIndicator(
                        progress = { etat.progression / etat.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Téléchargement des tuiles… ${etat.progression}/${etat.total} (zoom ${etat.zoomActuel})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Téléchargement des tuiles…", style = MaterialTheme.typography.bodySmall)
                }
            }
            is EtatTuiles.Reussi -> Text(
                "Tuiles téléchargées (zooms $ZOOM_MIN à $ZOOM_MAX).",
                style = MaterialTheme.typography.bodySmall,
            )
            is EtatTuiles.Echec -> Text(
                "Échec du téléchargement (réseau indisponible ?). Le suivi fonctionne quand même.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            is EtatTuiles.Refuse -> Text(
                "Trajet trop long pour la carte hors ligne (${etat.tuiles} tuiles, plafond ${SourceTuiles.TUILLES_MAX}).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            EtatTuiles.Repos -> {}
        }

        TextButton(onClick = relancerDetection, modifier = Modifier.fillMaxWidth()) {
            Text("Relancer la détection GPS")
        }

        BlocJournalPlantage(contexte)
    }

    if (dialogueAeroports) {
        DialogueAeroports(
            ecran = ecran,
            rechercher = rechercher,
            choisirDepart = choisirDepart,
            choisirArrivee = choisirArrivee,
            onFermer = { dialogueAeroports = false },
        )
    }
}

/** Carte de synthèse de l'écran d'accueil : libellé, valeur, détail, pastille. */
@Composable
private fun CarteDetection(
    titre: String,
    valeur: String,
    detail: String?,
    pastilleVerte: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(10.dp)
                    .height(10.dp)
                    .background(
                        if (pastilleVerte) Color(0xFF2E7D32) else Color(0xFFE08A00),
                        shape = CircleShape,
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(titre, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(valeur, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/**
 * Correction manuelle des deux aéroports, ouverte à la demande : ce n'est plus
 * une étape obligatoire de la préparation.
 */
@Composable
private fun DialogueAeroports(
    ecran: EcranVol,
    rechercher: (String) -> List<Aeroport>,
    choisirDepart: (Aeroport) -> Unit,
    choisirArrivee: (Aeroport) -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Corriger les aéroports") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChampRechercheAeroport(
                    label = "Aéroport de départ",
                    selection = ecran.depart,
                    rechercher = rechercher,
                    onChoisir = choisirDepart,
                )
                Spacer(Modifier.height(10.dp))
                ChampRechercheAeroport(
                    label = "Aéroport d'arrivée",
                    selection = ecran.arrivee,
                    rechercher = rechercher,
                    onChoisir = choisirArrivee,
                )
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Terminé") } },
    )
}

/**
 * Écran de vol : carte osmdroid plein écran (position, marqueurs, trajet),
 * bandeau de mesures calculées via [VolSuivi], et arrêt du suivi. L'écran reste
 * allumé pendant tout le vol.
 */
@Composable
fun EcranSuiviVol(
    ecran: EcranVol,
    reglages: ReglagesVol,
    rechercher: (String) -> List<Aeroport>,
    corrigerArrivee: (Aeroport) -> Unit,
    arreterSuivi: () -> Unit,
    onOuvrirParametres: () -> Unit,
) {
    var dialogueCorrection by remember { mutableStateOf(false) }

    // Écran maintenu allumé : on ne veut pas que la luminosité baisse en vol.
    val vue = LocalView.current
    DisposableEffect(Unit) {
        vue.keepScreenOn = true
        onDispose { vue.keepScreenOn = false }
    }

    // Mode alterné : on bascule entre cadrans et carte par créneaux.
    val debutVol = remember { android.os.SystemClock.elapsedRealtime() }
    var ecouleMs by remember { mutableStateOf(0L) }
    LaunchedEffect(reglages.alterne, reglages.dureeAlternanceSec) {
        if (!reglages.alterne) return@LaunchedEffect
        while (true) {
            ecouleMs = android.os.SystemClock.elapsedRealtime() - debutVol
            kotlinx.coroutines.delay(250)
        }
    }
    val creneauCadrans = Alternance.cadransAffiches(ecouleMs, reglages.dureeAlternanceSec)
    val montrerCarte = when (reglages.mode) {
        ModeAffichage.CARTE -> true
        ModeAffichage.CADRANS -> false
        ModeAffichage.DEUX -> !reglages.alterne || !creneauCadrans
    }
    val montrerCadrans = when (reglages.mode) {
        ModeAffichage.CARTE -> false
        ModeAffichage.CADRANS -> true
        ModeAffichage.DEUX -> !reglages.alterne || creneauCadrans
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (montrerCarte) {
                CarteVol(
                    depart = ecran.depart,
                    arrivee = ecran.arrivee,
                    dernier = ecran.dernier,
                    points = ecran.points,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (montrerCarte && montrerCadrans) {
                // Voile léger : les tuiles restent lisibles, les cadrans se détachent.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
            }
            if (montrerCadrans) {
                TableauDeBord(
                    dernier = ecran.dernier,
                    modifier = Modifier.fillMaxSize(),
                    opacitePanneaux = reglages.transparenceCadrans,
                )
            }
            // Accès direct au paramétrage, y compris en plein vol.
            IconButton(
                onClick = onOuvrirParametres,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Paramétrage",
                    tint = Color.White,
                )
            }
        }
        BandeauVol(ecran = ecran, onCorriger = { dialogueCorrection = true })
        Button(onClick = arreterSuivi, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(Icons.Filled.Stop, null)
            Spacer(Modifier.width(8.dp))
            Text("Arrêter")
        }
    }

    if (dialogueCorrection) {
        DialogueCorrectionArrivee(
            rechercher = rechercher,
            corrigerArrivee = corrigerArrivee,
            onFermer = { dialogueCorrection = false },
        )
    }
}

/**
 * Écran d'arrivée : résumé du vol (durée, distance) et partage du journal GPX,
 * puis remise à zéro pour un nouveau vol.
 */
@Composable
fun EcranArrivee(ecran: EcranVol, nouveauVol: () -> Unit) {
    val contexte = LocalContext.current
    val points = ecran.points

    // Durée du suivi : écart entre le premier et le dernier point enregistré.
    val dureeMs = if (points.size >= 2) {
        (points.last().horodatageMs - points.first().horodatageMs).coerceAtLeast(0L)
    } else 0L
    val minutes = dureeMs / 60_000L
    val dureeTexte = if (minutes >= 60) {
        "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min"
    } else {
        "$minutes min"
    }
    // Distance parcourue : somme des segments entre points consécutifs.
    val distanceKm = if (points.size >= 2) {
        points.zipWithNext().sumOf { (a, b) -> Geo.distanceKm(a.lat, a.lon, b.lat, b.lon) }
    } else 0.0

    var erreurPartage by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Arrivée", style = MaterialTheme.typography.headlineMedium)

        if (points.isEmpty()) {
            Text(
                "Aucun point GPS n'a été enregistré pendant ce vol.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LigneResume("Durée du suivi", dureeTexte)
                    LigneResume("Distance parcourue", "${distanceKm.roundToInt()} km")
                    LigneResume("Points enregistrés", points.size.toString())
                }
            }
        }

        Button(
            onClick = { erreurPartage = partagerGpx(contexte, points) },
            enabled = points.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("Partager le GPX")
        }
        erreurPartage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = nouveauVol, modifier = Modifier.fillMaxWidth()) {
            Text("Nouveau vol")
        }
    }
}

// ---------------------------------------------------------------------------
// Composants réutilisables
// ---------------------------------------------------------------------------

/**
 * Champ de recherche d'aéroport avec suggestions (code, nom, ville, pays). Le
 * texte est toujours effaçable ; l'aéroport retenu s'affiche sous le champ et
 * se remplace en en choisissant un nouveau dans les suggestions.
 */
@Composable
private fun ChampRechercheAeroport(
    label: String,
    selection: Aeroport?,
    rechercher: (String) -> List<Aeroport>,
    onChoisir: (Aeroport) -> Unit,
) {
    var texte by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Aeroport>>(emptyList()) }
    // Recherche débouncée : la frappe déclenchait auparavant un balayage complet
    // de la base sur le thread principal à chaque caractère, d'où la lenteur.
    LaunchedEffect(texte) {
        val requete = texte.trim()
        if (requete.length < 2) {
            suggestions = emptyList()
        } else {
            delay(180) // laisse le temps de finir de taper
            suggestions = withContext(Dispatchers.Default) { rechercher(requete) }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = texte,
            onValueChange = { texte = it },
            label = { Text(label) },
            placeholder = { Text("IATA, OACI, nom ou ville…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (texte.isNotEmpty()) {
                { IconButton(onClick = { texte = "" }) { Icon(Icons.Filled.Close, "Effacer la saisie") } }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (selection != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Place, null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(libelleAeroport(selection), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            Surface(
                tonalElevation = 3.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    suggestions.forEachIndexed { index, aeroport ->
                        Text(
                            libelleAeroport(aeroport),
                            Modifier.fillMaxWidth()
                                .clickable { onChoisir(aeroport); texte = "" }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (index < suggestions.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * Dialogue de correction de l'arrivée devinée : recherche puis choix manuel,
 * ce qui verrouille la devinette côté ViewModel (corrigerArrivee).
 */
@Composable
private fun DialogueCorrectionArrivee(
    rechercher: (String) -> List<Aeroport>,
    corrigerArrivee: (Aeroport) -> Unit,
    onFermer: () -> Unit,
) {
    var texte by remember { mutableStateOf("") }
    val suggestions = if (texte.trim().length >= 2) rechercher(texte) else emptyList()

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Corriger la destination") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = texte,
                    onValueChange = { texte = it },
                    label = { Text("Aéroport d'arrivée") },
                    singleLine = true,
                )
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(suggestions) { aeroport ->
                        Text(
                            libelleAeroport(aeroport),
                            Modifier.fillMaxWidth()
                                .clickable { corrigerArrivee(aeroport); onFermer() }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

/** Bandeau d'alerte (permission refusée, fournisseur GPS absent…). */
@Composable
private fun BandeauAlerte(message: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Carte osmdroid : trajet (ligne), marqueurs départ/arrivée et position courante.
 * Cadre automatiquement une seule fois au premier point d'ancrage connu, pour ne
 * pas lutter contre le zoom/pan manuel de l'utilisateur.
 */
@Composable
private fun CarteVol(
    depart: Aeroport?,
    arrivee: Aeroport?,
    dernier: PointVol?,
    points: List<PointVol>,
    modifier: Modifier = Modifier,
) {
    val carte = remember { mutableStateOf<MapView?>(null) }
    val aCadre = remember { mutableStateOf(false) }
    val contexte = LocalContext.current
    // Sans réseau et sans tuiles déjà en cache, la carte reste vide : on le dit
    // au lieu de laisser un grand rectangle gris (cas du vol sans wifi).
    var horsLigne by remember { mutableStateOf(false) }
    LaunchedEffect(dernier) {
        horsLigne = !reseauDisponible(contexte)
    }

    Box(modifier) {
        AndroidView(
        factory = { contexte ->
            MapView(contexte).apply {
                setTileSource(SourceTuiles.OSM)
                setMultiTouchControls(true)
                setUseDataConnection(true)
                carte.value = this
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { vue ->
            // Reconstruit les calques : ligne du trajet puis marqueurs.
            vue.overlays.clear()
            val trace = points.map { GeoPoint(it.lat, it.lon) }
            if (trace.size >= 2) {
                val ligne = Polyline(vue).apply {
                    setPoints(trace)
                    // API non dépréciée : on règle le pinceau de contour direct.
                    outlinePaint.color = COULEUR_TRACE
                    outlinePaint.strokeWidth = 6f
                }
                vue.overlays.add(ligne)
            }
            depart?.let { vue.overlays.add(marqueur(vue, it.lat, it.lon, "Départ")) }
            arrivee?.let { vue.overlays.add(marqueur(vue, it.lat, it.lon, "Arrivée")) }
            dernier?.let { vue.overlays.add(marqueur(vue, it.lat, it.lon, "Position")) }

            if (!aCadre.value) {
                val ancres = buildList {
                    depart?.let { add(GeoPoint(it.lat, it.lon)) }
                    arrivee?.let { add(GeoPoint(it.lat, it.lon)) }
                    dernier?.let { add(GeoPoint(it.lat, it.lon)) }
                }
                when (ancres.size) {
                    0 -> Unit // aucune position connue : carte monde par défaut.
                    1 -> {
                        vue.controller.setZoom(6.0)
                        vue.controller.setCenter(ancres[0])
                    }
                    else -> vue.zoomToBoundingBox(
                        BoundingBox.fromGeoPoints(ancres).increaseByScale(1.4f),
                        false,
                    )
                }
                if (ancres.isNotEmpty()) aCadre.value = true
            }
            vue.invalidate()
        },
        )

        // Cycle de vie osmdroid : sans onResume(), le fournisseur de tuiles peut
        // rester à l'arrêt et la carte s'affiche vide.
        LaunchedEffect(carte.value) { carte.value?.onResume() }

        if (horsLigne) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
            ) {
                Text(
                    "Carte hors ligne : pas de réseau et aucune tuile en cache. " +
                        "Utilise « Préparer la carte » avant le décollage.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            carte.value?.onPause()
            carte.value?.onDetach()
        }
    }
}

/** Vrai si une connexion réseau est disponible (pour expliquer une carte vide). */
private fun reseauDisponible(contexte: Context): Boolean {
    return try {
        val gestionnaire = contexte.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return true
        val reseau = gestionnaire.activeNetwork ?: return false
        val capacites = gestionnaire.getNetworkCapabilities(reseau) ?: return false
        capacites.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Throwable) {
        true
    }
}

/** Marqueur simple, ancré par le bas, avec un titre pour l'info-bulle. */
private fun marqueur(carte: MapView, lat: Double, lon: Double, titre: String): Marker =
    Marker(carte).apply {
        position = GeoPoint(lat, lon)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = titre
    }

/**
 * Bandeau de mesures du vol : altitude, vitesse sol, cap, vitesse verticale,
 * distance et durée restantes. Sans point GPS ou sans destination, on affiche un
 * libellé neutre (« destination inconnue ») au lieu de planter.
 */
@Composable
private fun BandeauVol(ecran: EcranVol, onCorriger: () -> Unit) {
    val dernier = ecran.dernier
    val arrivee = ecran.arrivee

    val distanceRestante = if (dernier != null && arrivee != null) {
        VolSuivi.distanceRestanteKm(dernier.lat, dernier.lon, arrivee)
    } else null
    // Vitesse moyenne sur les points exploitables (vitesse sol strictement positive).
    val vitesses = ecran.points.map { it.vitesseSolKmh }.filter { it > 0.0 }
    val vitesseMoyenne = if (vitesses.isNotEmpty()) vitesses.average() else null
    val dureeRestante = if (distanceRestante != null && dernier != null) {
        VolSuivi.dureeRestanteMin(distanceRestante, dernier.vitesseSolKmh, vitesseMoyenne)
    } else null

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ecran.origine == OrigineVol.DEVINEE && arrivee != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Destination probable : ${arrivee.nom} " +
                            "(${(confiance(ecran) * 100).roundToInt()} %)",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    TextButton(onClick = onCorriger) { Text("Corriger") }
                }
            }
            if (dernier == null) {
                Text("En attente du premier point GPS…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mesure("Altitude", "${dernier.altitudeM.roundToInt()} m", Modifier.weight(1f))
                    Mesure("Vitesse sol", "${dernier.vitesseSolKmh.roundToInt()} km/h", Modifier.weight(1f))
                    Mesure("Cap", "${dernier.capDeg.roundToInt()}°", Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mesure("Vitesse verticale", formatVitesseVerticale(dernier.vitesseVerticaleMs), Modifier.weight(1f))
                    Mesure(
                        "Distance restante",
                        if (distanceRestante != null) "${distanceRestante.roundToInt()} km" else "destination inconnue",
                        Modifier.weight(1f),
                    )
                    Mesure(
                        "Durée restante",
                        if (dureeRestante != null) "${dureeRestante} min" else "—",
                        Modifier.weight(1f),
                    )
                }
            }
            ecran.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Petite cellule « libellé / valeur » du bandeau de vol. */
@Composable
private fun Mesure(label: String, valeur: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valeur, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Ligne « libellé / valeur » du résumé d'arrivée. */
@Composable
private fun LigneResume(label: String, valeur: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valeur, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Aides : libellé, vitesse verticale, confiance, tuiles, partage
// ---------------------------------------------------------------------------

/** Libellé d'aéroport : code (IATA sinon OACI), nom, ville, pays. */
private fun libelleAeroport(aeroport: Aeroport): String {
    val code = when {
        aeroport.iata.isNotEmpty() -> aeroport.iata
        aeroport.icao.isNotEmpty() -> aeroport.icao
        else -> aeroport.code
    }
    val localisation = listOf(aeroport.ville, aeroport.pays).filter { it.isNotBlank() }.joinToString(", ")
    return if (localisation.isNotEmpty()) "$code · ${aeroport.nom} · $localisation" else "$code · ${aeroport.nom}"
}

/** Vitesse verticale lisible, avec une flèche de tendance. */
private fun formatVitesseVerticale(v: Double): String {
    val fleche = when {
        v > 0.5 -> "↗"
        v < -0.5 -> "↘"
        else -> "→"
    }
    return "$fleche ${"%.1f".format(v)} m/s"
}

/** Confiance de la devinette, 0 si aucun état de vol connu. */
private fun confiance(ecran: EcranVol): Double = ecran.etat?.confiance ?: 0.0

/** État du téléchargement des tuiles hors ligne. */
private sealed interface EtatTuiles {
    object Repos : EtatTuiles
    data class EnCours(val progression: Int, val total: Int, val zoomActuel: Int) : EtatTuiles
    object Reussi : EtatTuiles
    data class Echec(val erreurs: Int) : EtatTuiles

    /** Trajet trop long : on refuse plutôt que de moissonner le serveur. */
    data class Refuse(val tuiles: Int) : EtatTuiles
}

/**
 * Télécharge les tuiles (zooms [ZOOM_MIN] à [ZOOM_MAX]) autour du trajet
 * départ/arrivée via osmdroid [CacheManager], en publiant la progression sur le
 * thread principal. Échoue proprement sans réseau.
 */
private fun lancerTelechargementTuiles(
    contexte: Context,
    depart: Aeroport,
    arrivee: Aeroport,
    onEtat: (EtatTuiles) -> Unit,
) {
    // Les rappels de CacheManager arrivent sur un thread d'arrière-plan : on
    // repasse sur le thread principal avant de toucher à l'état Compose.
    val principal = Handler(Looper.getMainLooper())
    val publier: (EtatTuiles) -> Unit = { etat -> principal.post { onEtat(etat) } }

    try {
        // Carte jetable : sert uniquement à obtenir le fournisseur de tuiles et
        // le cache que CacheManager va remplir.
        val carte = MapView(contexte)
        carte.setTileSource(SourceTuiles.OSM)
        val gestionnaire = CacheManager(carte)

        // Boîte englobante départ + arrivée, élargie pour couvrir le trajet.
        val boite = BoundingBox.fromGeoPoints(
            listOf(GeoPoint(depart.lat, depart.lon), GeoPoint(arrivee.lat, arrivee.lon))
        ).increaseByScale(1.5f)

        // Pré-vérification : inutile de lancer une AsyncTask qui lèverait une
        // exception non interceptable ou moissonnerait le serveur.
        val tuilesPrevues = gestionnaire.possibleTilesInArea(boite, ZOOM_MIN, ZOOM_MAX)
        if (tuilesPrevues <= 0) {
            publier(EtatTuiles.Echec(0))
            return
        }
        if (tuilesPrevues > SourceTuiles.TUILLES_MAX) {
            JournalPlantage.etape("téléchargement de tuiles refusé ($tuilesPrevues tuiles)")
            publier(EtatTuiles.Refuse(tuilesPrevues))
            return
        }

        var total = 0
        gestionnaire.downloadAreaAsync(
            contexte,
            boite,
            ZOOM_MIN,
            ZOOM_MAX,
            object : CacheManager.CacheManagerCallback {
                override fun downloadStarted() {
                    publier(EtatTuiles.EnCours(0, 0, ZOOM_MIN))
                }
                override fun setPossibleTilesInArea(totalTuiles: Int) {
                    total = totalTuiles
                    publier(EtatTuiles.EnCours(0, totalTuiles, ZOOM_MIN))
                }
                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                    publier(EtatTuiles.EnCours(progress, total, currentZoomLevel))
                }
                override fun onTaskComplete() {
                    publier(EtatTuiles.Reussi)
                }
                override fun onTaskFailed(errors: Int) {
                    publier(EtatTuiles.Echec(errors))
                }
            },
        )
    } catch (e: Exception) {
        // Sans réseau (ou toute autre panne d'initialisation) : échec propre.
        publier(EtatTuiles.Echec(0))
    }
}

/**
 * Écrit le journal GPX dans cacheDir/gpx puis ouvre le sélecteur de partage via
 * FileProvider. Retourne un message d'erreur lisible si le partage échoue.
 */
private fun partagerGpx(contexte: Context, points: List<PointVol>): String? {
    return try {
        val nom = JournalGpx.nomFichier(LocalDate.now())
        val dossier = File(contexte.cacheDir, "gpx")
        dossier.mkdirs()
        val fichier = File(dossier, nom)
        fichier.writeText(JournalGpx.construire(points))

        val uri = FileProvider.getUriForFile(contexte, "${contexte.packageName}.fileprovider", fichier)
        val intention = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, nom)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        contexte.startActivity(Intent.createChooser(intention, "Partager le GPX"))
        null
    } catch (e: Exception) {
        "Partage impossible : ${e.message ?: "aucun récepteur disponible."}"
    }
}

/**
 * Bloc discret du journal de plantage : n'apparaît que si un plantage a été
 * enregistré, avec un partage manuel et un effacement.
 */
@Composable
private fun BlocJournalPlantage(contexte: android.content.Context) {
    var plantage by remember { mutableStateOf(JournalPlantage.dernierPlantage(contexte)) }
    val contenu = plantage ?: return
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("Dernier plantage", style = MaterialTheme.typography.titleSmall)
    Text(
        contenu.lineSequence().take(6).joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    Row {
        TextButton(onClick = { contexte.startActivity(JournalPlantage.intentionPartage(contexte)) }) {
            Text("Envoyer le journal")
        }
        TextButton(onClick = {
            JournalPlantage.effacer(contexte)
            plantage = null
        }) { Text("Effacer") }
    }
}
