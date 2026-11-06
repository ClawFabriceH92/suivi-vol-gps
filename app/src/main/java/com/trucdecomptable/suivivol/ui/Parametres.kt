package com.trucdecomptable.suivivol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trucdecomptable.suivivol.data.DUREES_ALTERNANCE
import com.trucdecomptable.suivivol.data.ModeAffichage
import com.trucdecomptable.suivivol.data.ReglagesVol
import kotlin.math.roundToInt

/**
 * Onglet Paramétrage de l'écran de vol.
 *
 * Trois réglages, tous appliqués en direct pendant le vol :
 * - ce qu'on veut voir : carte seule, cadrans seuls, ou les deux superposés ;
 * - le mode alterné : cadrans et carte à tour de rôle, par créneaux ;
 * - la transparence des panneaux de cadrans, pour doser la carte visible dessous.
 */
@Composable
fun EcranParametres(
    reglages: ReglagesVol,
    onMode: (ModeAffichage) -> Unit,
    onAlterne: (Boolean) -> Unit,
    onDuree: (Int) -> Unit,
    onTransparence: (Int) -> Unit,
    onRetour: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRetour) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Text("Paramétrage", style = MaterialTheme.typography.headlineSmall)
        }

        // ---------- 1. Ce qu'on affiche ----------
        Text("Affichage en vol", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Choisis ce que tu veux voir pendant le suivi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = reglages.mode == ModeAffichage.CARTE,
                onClick = { onMode(ModeAffichage.CARTE) },
                label = { Text("Carte seule") },
            )
            FilterChip(
                selected = reglages.mode == ModeAffichage.CADRANS,
                onClick = { onMode(ModeAffichage.CADRANS) },
                label = { Text("Cadrans seuls") },
            )
            FilterChip(
                selected = reglages.mode == ModeAffichage.DEUX,
                onClick = { onMode(ModeAffichage.DEUX) },
                label = { Text("Les deux") },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        // ---------- 2. Mode alterné ----------
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mode alterné", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Cadrans puis carte, à tour de rôle. Utile pour garder les deux sans surcharger l'écran.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Switch(checked = reglages.alterne, onCheckedChange = onAlterne)
        }
        if (reglages.alterne) {
            Text("Durée de chaque vue", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DUREES_ALTERNANCE.forEach { secondes ->
                    FilterChip(
                        selected = reglages.dureeAlternanceSec == secondes,
                        onClick = { onDuree(secondes) },
                        label = { Text("$secondes s") },
                    )
                }
            }
            if (reglages.mode != ModeAffichage.DEUX) {
                Text(
                    "L'alternance ne s'applique qu'au mode « Les deux » : en carte seule ou " +
                        "cadrans seuls, il n'y a rien à alterner.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        // ---------- 3. Transparence ----------
        Text("Transparence des cadrans", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "À 100 % les panneaux masquent la carte, à 0 % il ne reste que les traits des " +
                "cadrans sur la carte.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Slider(
            value = reglages.transparenceCadrans.toFloat(),
            onValueChange = { onTransparence(it.roundToInt()) },
            valueRange = 0f..100f,
            enabled = reglages.mode == ModeAffichage.DEUX,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${reglages.transparenceCadrans} %",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (reglages.mode != ModeAffichage.DEUX) {
            Text(
                "Réglage disponible uniquement quand la carte est affichée derrière les cadrans.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(
            "Les réglages sont enregistrés sur le téléphone et s'appliquent immédiatement, " +
                "même en plein vol.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
