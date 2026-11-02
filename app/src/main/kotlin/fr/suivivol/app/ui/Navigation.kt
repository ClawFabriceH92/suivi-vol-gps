package fr.suivivol.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.suivivol.app.data.Graphe
import fr.suivivol.app.data.Reglages
import fr.suivivol.app.ui.theme.ThemeSuiviVol

object Routes {
    const val PREPARATION = "preparation"
    const val VOL = "vol"
    const val RESUME = "resume/{volId}"
    const val REGLAGES = "reglages"
    const val A_PROPOS = "apropos"
    fun resume(volId: Long) = "resume/$volId"
}

@Composable
fun AppSuiviVol(volActifInitial: Long?) {
    val reglages by Graphe.reglages.flux.collectAsStateWithLifecycle(initialValue = Reglages())
    ThemeSuiviVol(reglages.theme) {
        val nav = rememberNavController()
        NavHost(
            navController = nav,
            startDestination = if (volActifInitial != null) Routes.VOL else Routes.PREPARATION,
        ) {
            composable(Routes.PREPARATION) {
                EcranPreparation(
                    onDecolle = {
                        nav.navigate(Routes.VOL) { popUpTo(Routes.PREPARATION) { inclusive = true } }
                    },
                    onOuvrirReglages = { nav.navigate(Routes.REGLAGES) },
                    onOuvrirResume = { id -> nav.navigate(Routes.resume(id)) },
                )
            }
            composable(Routes.VOL) {
                EcranVol(
                    onVolTermine = { id ->
                        nav.navigate(Routes.resume(id)) { popUpTo(Routes.VOL) { inclusive = true } }
                    },
                    onOuvrirReglages = { nav.navigate(Routes.REGLAGES) },
                    onRetourPreparation = {
                        nav.navigate(Routes.PREPARATION) { popUpTo(Routes.VOL) { inclusive = true } }
                    },
                )
            }
            composable(
                Routes.RESUME,
                arguments = listOf(navArgument("volId") { type = NavType.LongType }),
            ) { entree ->
                val volId = entree.arguments?.getLong("volId") ?: 0L
                EcranResume(
                    volId = volId,
                    onNouveauVol = {
                        nav.navigate(Routes.PREPARATION) { popUpTo(0) { inclusive = true } }
                    },
                    onRetour = { nav.popBackStack() },
                )
            }
            composable(Routes.REGLAGES) {
                EcranReglages(
                    onRetour = { nav.popBackStack() },
                    onOuvrirAPropos = { nav.navigate(Routes.A_PROPOS) },
                )
            }
            composable(Routes.A_PROPOS) {
                EcranAPropos(onRetour = { nav.popBackStack() })
            }
        }
    }
}
