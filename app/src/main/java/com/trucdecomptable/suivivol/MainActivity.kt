package com.trucdecomptable.suivivol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Point d'entrée de l'app. Les trois écrans (Préparation, Vol, Arrivée) arrivent
 * avec la v1 ; ce squelette ne fait que prouver que le projet se construit et
 * que la coquille Compose démarre.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Accueil()
                }
            }
        }
    }
}

@Composable
private fun Accueil() {
    Column(Modifier.padding(24.dp)) {
        Text("Suivi de vol GPS", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Suivi de ton vol avec le seul GPS du téléphone : position, altitude, " +
                "vitesse sol, distance et durée restante jusqu'à l'aéroport d'arrivée.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Squelette de la v0.1.0 : écrans et moteur de devinette à venir.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
