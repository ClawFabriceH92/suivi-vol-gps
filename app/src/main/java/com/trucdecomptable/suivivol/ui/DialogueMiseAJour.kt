package com.trucdecomptable.suivivol.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trucdecomptable.suivivol.update.UpdateManager

/**
 * Fenêtre de mise à jour : GitHub Releases sert la dernière version, l'app
 * propose de la télécharger puis de l'installer. Rien n'est imposé : « Plus
 * tard » ferme la fenêtre et le prochain lancement reproposera.
 */
@Composable
fun DialogueMiseAJour(
    etat: UpdateManager.State,
    onTelecharger: () -> Unit,
    onInstaller: () -> Unit,
    onAutoriser: () -> Unit,
    onFermer: () -> Unit,
) {
    when (etat) {
        is UpdateManager.State.Available -> AlertDialog(
            onDismissRequest = onFermer,
            title = { Text("Version ${etat.version} disponible") },
            text = { Text(etat.notes.ifBlank { "Une nouvelle version est prête." }) },
            confirmButton = { TextButton(onClick = onTelecharger) { Text("Télécharger") } },
            dismissButton = { TextButton(onClick = onFermer) { Text("Plus tard") } },
        )

        is UpdateManager.State.Downloading -> AlertDialog(
            onDismissRequest = {},
            // Le pourcentage vit dans le titre : il reste lisible quelle que
            // soit la place laissée par la barre.
            title = { Text("Téléchargement… ${(etat.progress * 100).toInt()} %") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { etat.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "${(etat.progress * 100).toInt()} % téléchargé",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            },
            confirmButton = {},
        )

        is UpdateManager.State.ReadyToInstall -> AlertDialog(
            onDismissRequest = onFermer,
            title = { Text("Prêt à installer") },
            text = { Text("La version ${etat.version} est téléchargée : Android va demander confirmation.") },
            confirmButton = { TextButton(onClick = onInstaller) { Text("Installer") } },
            dismissButton = { TextButton(onClick = onFermer) { Text("Plus tard") } },
        )

        is UpdateManager.State.UpToDate -> AlertDialog(
            onDismissRequest = onFermer,
            title = { Text("À jour") },
            text = { Text("Version installée : ${etat.version}") },
            confirmButton = { TextButton(onClick = onFermer) { Text("OK") } },
        )

        is UpdateManager.State.Failed -> AlertDialog(
            onDismissRequest = onFermer,
            title = { Text("Vérification impossible") },
            text = { Text(etat.detail ?: "GitHub n'a pas répondu.") },
            confirmButton = { TextButton(onClick = onFermer) { Text("OK") } },
            dismissButton = { TextButton(onClick = onAutoriser) { Text("Autoriser l'installation") } },
        )

        UpdateManager.State.Idle -> Unit
    }
}
