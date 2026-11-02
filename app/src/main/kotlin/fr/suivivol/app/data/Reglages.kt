package fr.suivivol.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.suivivol.core.UniteAltitude
import fr.suivivol.core.UniteVitesse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeApp { SYSTEME, CLAIR, SOMBRE }

/** Réglages de l'application (cahier des charges §4.4). */
data class Reglages(
    val vitesseCroisiereKmh: Int = 850,
    val uniteAltitude: UniteAltitude = UniteAltitude.METRES,
    val uniteVitesse: UniteVitesse = UniteVitesse.KMH,
    val frequenceSecondes: Int = 1,
    val garderEcranAllume: Boolean = true,
    val theme: ThemeApp = ThemeApp.SYSTEME,
    /** Identifiant du vol en cours (null si aucun vol actif). */
    val volActifId: Long? = null,
) {
    companion object {
        val FREQUENCES_SECONDES = listOf(1, 5, 15)
    }
}

private val Context.magasin: DataStore<Preferences> by preferencesDataStore(name = "reglages")

class ReglagesRepository(private val context: Context) {
    private object Cles {
        val VITESSE = intPreferencesKey("vitesse_croisiere_kmh")
        val UNITE_ALT = stringPreferencesKey("unite_altitude")
        val UNITE_VIT = stringPreferencesKey("unite_vitesse")
        val FREQUENCE = intPreferencesKey("frequence_s")
        val ECRAN = booleanPreferencesKey("garder_ecran")
        val THEME = stringPreferencesKey("theme")
        val VOL_ACTIF = longPreferencesKey("vol_actif_id")
    }

    val flux: Flow<Reglages> = context.magasin.data.map { p ->
        Reglages(
            vitesseCroisiereKmh = p[Cles.VITESSE] ?: 850,
            uniteAltitude = enumOuDefaut(p[Cles.UNITE_ALT], UniteAltitude.METRES),
            uniteVitesse = enumOuDefaut(p[Cles.UNITE_VIT], UniteVitesse.KMH),
            frequenceSecondes = p[Cles.FREQUENCE] ?: 1,
            garderEcranAllume = p[Cles.ECRAN] ?: true,
            theme = enumOuDefaut(p[Cles.THEME], ThemeApp.SYSTEME),
            volActifId = p[Cles.VOL_ACTIF]?.takeIf { it > 0 },
        )
    }

    suspend fun actuels(): Reglages = flux.first()

    suspend fun definirVitesseCroisiere(kmh: Int) = context.magasin.edit { it[Cles.VITESSE] = kmh.coerceIn(100, 2000) }
    suspend fun definirUniteAltitude(u: UniteAltitude) = context.magasin.edit { it[Cles.UNITE_ALT] = u.name }
    suspend fun definirUniteVitesse(u: UniteVitesse) = context.magasin.edit { it[Cles.UNITE_VIT] = u.name }
    suspend fun definirFrequence(secondes: Int) = context.magasin.edit { it[Cles.FREQUENCE] = secondes }
    suspend fun definirGarderEcran(oui: Boolean) = context.magasin.edit { it[Cles.ECRAN] = oui }
    suspend fun definirTheme(t: ThemeApp) = context.magasin.edit { it[Cles.THEME] = t.name }
    suspend fun definirVolActif(id: Long?) = context.magasin.edit { p ->
        if (id == null) p.remove(Cles.VOL_ACTIF) else p[Cles.VOL_ACTIF] = id
    }

    private inline fun <reified E : Enum<E>> enumOuDefaut(nom: String?, defaut: E): E =
        nom?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: defaut
}
