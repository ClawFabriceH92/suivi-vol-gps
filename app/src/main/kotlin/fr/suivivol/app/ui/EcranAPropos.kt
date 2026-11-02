package fr.suivivol.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.suivivol.app.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranAPropos(onRetour: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("À propos") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text("Suivi de vol GPS ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall)
            Text("Build ${BuildConfig.DATE_BUILD} · github.com/${BuildConfig.DEPOT_GITHUB}", style = MaterialTheme.typography.bodySmall)

            Bloc(
                "Ce que fait l'application",
                "Elle suit un vol avec le seul GPS du téléphone : position sur une carte OpenStreetMap, altitude, " +
                    "vitesse sol, distance et durée restantes jusqu'à l'aéroport d'arrivée. Tout fonctionne en mode avion, " +
                    "sans compte et sans serveur.",
            )
            Bloc(
                "Ce qu'elle n'est pas",
                "Ce n'est pas un instrument de navigation aéronautique : usage informatif uniquement. Elle ne remplace ni " +
                    "les écrans de cabine ni un service de suivi de vol : aucune donnée officielle, pas d'heure de décollage " +
                    "fournie par la compagnie.",
            )
            Bloc(
                "Limites physiques",
                "• Le GNSS fonctionne en mode avion (la puce ne fait que recevoir), mais l'accrochage prend quelques minutes " +
                    "et seul le côté hublot garantit une réception correcte.\n" +
                    "• L'altitude GPS est imprécise (± 20 à 50 m, biais ellipsoïdal) : le baromètre du téléphone, s'il existe, " +
                    "donne une valeur plus stable, recalée sur le GPS.\n" +
                    "• Dans une cabine pressurisée, le baromètre mesure la pression de la cabine (≈ 1 500 à 2 500 m " +
                    "équivalents), pas celle de l'extérieur : l'application le détecte, affiche alors l'altitude GPS en " +
                    "gros et l'altitude cabine en petit.\n" +
                    "• La vitesse GPS est une vitesse sol : elle inclut le vent (jusqu'à ± 200 km/h en jet-stream).\n" +
                    "• La durée restante ignore les détours (attente, approche) : comptez 20 à 30 minutes de plus.\n" +
                    "• Gardez le mode avion activé avec la localisation en marche : c'est la configuration recommandée.",
            )
            Bloc(
                "Vie privée et réseau",
                "Aucune donnée ne quitte le téléphone : pas de compte, pas de télémétrie, pas d'analytique. " +
                    "La permission INTERNET sert uniquement au téléchargement des tuiles de carte avant le vol et à la " +
                    "vérification manuelle de mise à jour depuis l'écran Réglages.",
            )
            Bloc(
                "Sources et licences",
                "• Aéroports : OurAirports (ourairports.com/data), domaine public, filtrés sur les aéroports grands et moyens " +
                    "à service régulier.\n" +
                    "• Fond de carte : © OpenStreetMap contributors, données sous ODbL, tuiles de tile.openstreetmap.org " +
                    "utilisées selon la politique d'usage de la fondation OSM.\n" +
                    "• Bibliothèque de carte : osmdroid (Apache 2.0).\n" +
                    "• Calculs : haversine (rayon 6 371 008,8 m), cap orthodromique, altitude ICAO " +
                    "h = 44 330 × (1 − (P/1013,25)^0,1903).",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Bloc(titre: String, texte: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titre, style = MaterialTheme.typography.titleMedium)
            Text(texte, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
