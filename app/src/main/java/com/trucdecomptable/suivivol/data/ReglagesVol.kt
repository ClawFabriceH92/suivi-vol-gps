package com.trucdecomptable.suivivol.data

import android.content.Context
import android.content.SharedPreferences

/** Ce que l'utilisateur veut voir pendant le vol. */
enum class ModeAffichage {
    /** Carte seule, plein écran. */
    CARTE,

    /** Cadrans seuls, sans fond de carte. */
    CADRANS,

    /** Les deux : carte en fond, cadrans par-dessus. */
    DEUX,
}

/**
 * Réglages d'affichage de l'écran de vol.
 *
 * [transparenceCadrans] est l'opacité des panneaux de cadrans en pourcentage :
 * 100 = panneaux opaques (cadrans très lisibles, carte cachée dessous),
 * 0 = panneaux invisibles (seuls les traits des cadrans restent).
 */
data class ReglagesVol(
    val mode: ModeAffichage = ModeAffichage.DEUX,
    val alterne: Boolean = false,
    val dureeAlternanceSec: Int = 10,
    val transparenceCadrans: Int = 85,
)

/** Durées proposées pour le mode alterné. */
val DUREES_ALTERNANCE = listOf(5, 10, 20, 30)

/** Persistance des réglages d'affichage (SharedPreferences local, rien d'autre). */
class ReglagesVolStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("suivivol_reglages", Context.MODE_PRIVATE)

    fun charger(): ReglagesVol = ReglagesVol(
        mode = when (prefs.getString("mode", ModeAffichage.DEUX.name)) {
            ModeAffichage.CARTE.name -> ModeAffichage.CARTE
            ModeAffichage.CADRANS.name -> ModeAffichage.CADRANS
            else -> ModeAffichage.DEUX
        },
        alterne = prefs.getBoolean("alterne", false),
        dureeAlternanceSec = prefs.getInt("duree_alternance", 10),
        transparenceCadrans = prefs.getInt("transparence", 85).coerceIn(0, 100),
    )

    fun enregistrer(reglages: ReglagesVol) {
        prefs.edit()
            .putString("mode", reglages.mode.name)
            .putBoolean("alterne", reglages.alterne)
            .putInt("duree_alternance", reglages.dureeAlternanceSec)
            .putInt("transparence", reglages.transparenceCadrans)
            .apply()
    }
}
