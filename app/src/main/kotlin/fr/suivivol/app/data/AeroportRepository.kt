package fr.suivivol.app.data

import android.content.Context
import fr.suivivol.core.Aeroport
import fr.suivivol.core.RechercheAeroport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Base d'aéroports embarquée (assets/aeroports.json, générée par scripts/generer_aeroports.py).
 * Chargée une seule fois, à la demande, en lecture seule.
 */
class AeroportRepository(private val context: Context) {
    @Volatile
    private var recherche: RechercheAeroport? = null

    suspend fun charger(): RechercheAeroport {
        recherche?.let { return it }
        return withContext(Dispatchers.IO) {
            recherche ?: lire().also { recherche = it }
        }
    }

    private fun lire(): RechercheAeroport {
        val texte = context.assets.open("aeroports.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return RechercheAeroport(parser(texte))
    }

    suspend fun rechercher(requete: String, max: Int = 20): List<Aeroport> = charger().rechercher(requete, max)

    suspend fun parCode(code: String): Aeroport? = charger().parCode(code)

    companion object {
        /** Format : [oaci, iata, nom, ville, pays, lat, lon, altitude_ft|null, grand(0/1)]. */
        fun parser(json: String): List<Aeroport> {
            val tableau = JSONArray(json)
            val resultat = ArrayList<Aeroport>(tableau.length())
            for (i in 0 until tableau.length()) {
                val a = tableau.getJSONArray(i)
                val iata = a.optString(1, "").takeIf { it.isNotBlank() }
                val altitude = if (a.isNull(7)) null else a.optInt(7)
                resultat.add(
                    Aeroport(
                        oaci = a.getString(0),
                        iata = iata,
                        nom = a.getString(2),
                        ville = a.optString(3, "").takeIf { it.isNotBlank() },
                        paysIso = a.getString(4),
                        lat = a.getDouble(5),
                        lon = a.getDouble(6),
                        altitudeFt = altitude,
                        grand = a.optInt(8, 0) == 1,
                    ),
                )
            }
            return resultat
        }
    }
}
