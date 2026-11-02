package fr.suivivol.app.maj

import fr.suivivol.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vérification manuelle de mise à jour : lit `version.json` attaché à la dernière release GitHub
 * (release roulante). Aucun appel automatique : l'utilisateur appuie sur le bouton.
 */
object VerificateurMaj {
    sealed class Resultat {
        data class Disponible(val versionName: String, val versionCode: Int, val urlApk: String, val notes: String?) : Resultat()
        data object AJour : Resultat()
        data class Erreur(val message: String) : Resultat()
    }

    suspend fun verifier(): Resultat = withContext(Dispatchers.IO) {
        try {
            val connexion = (URL(BuildConfig.URL_VERSION_JSON).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SuiviVolGPS/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connexion.responseCode
                if (code != 200) return@withContext Resultat.Erreur("Réponse HTTP $code")
                val texte = connexion.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(texte)
                val versionCode = json.optInt("versionCode", 0)
                val versionName = json.optString("versionName", "?")
                val urlApk = json.optString("apk", BuildConfig.URL_RELEASES)
                val notes = json.optString("notes", "").takeIf { it.isNotBlank() }
                if (versionCode > BuildConfig.VERSION_CODE) {
                    Resultat.Disponible(versionName, versionCode, urlApk, notes)
                } else {
                    Resultat.AJour
                }
            } finally {
                connexion.disconnect()
            }
        } catch (e: Exception) {
            Resultat.Erreur(e.message ?: "Réseau indisponible")
        }
    }
}
