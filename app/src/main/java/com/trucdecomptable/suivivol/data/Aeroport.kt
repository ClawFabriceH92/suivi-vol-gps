package com.trucdecomptable.suivivol.data

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/**
 * Un aéroport de la base embarquée (OurAirports, domaine public).
 *
 * `code` est l'identifiant principal (code IATA quand il existe, sinon un code
 * local), `iata` et `icao` sont optionnels (champ vide si absent). `regulier`
 * indique un service régulier (colonne `regulier` du CSV : `yes` / `no`).
 */
data class Aeroport(
    val code: String,
    val iata: String,
    val icao: String,
    val nom: String,
    val lat: Double,
    val lon: Double,
    val pays: String,
    val ville: String,
    val regulier: Boolean,
)

/**
 * Accès à la base d'aéroports. Tout est statique et pur (testable en JUnit),
 * sauf [charger] qui lit le fichier depuis les assets et qui est le seul point
 * de contact avec le [Context].
 */
object Aeroports {

    @Volatile
    private var cache: List<Aeroport>? = null

    /** Regex précompilée : `\p{Mn}` retire les diacritiques après décomposition NFD. */
    private val DIACRITIQUES = Regex("\\p{Mn}+")

    /** Pénalité (km) appliquée aux terrains sans service régulier. */
    private const val PENALITE_NON_REGULIER_KM = 25.0

    /** Champs déjà normalisés, pour ne pas les recalculer à chaque recherche. */
    private class Entree(
        val aeroport: Aeroport,
        val code: String,
        val iata: String,
        val icao: String,
        val ville: String,
        val nom: String,
    )

    @Volatile
    private var indexSource: List<Aeroport>? = null

    @Volatile
    private var indexEntrees: List<Entree> = emptyList()

    private fun indexe(tous: List<Aeroport>): List<Entree> {
        if (indexSource === tous) return indexEntrees
        synchronized(this) {
            if (indexSource === tous) return indexEntrees
            val entrees = tous.map { a ->
                Entree(a, normaliser(a.code), normaliser(a.iata), normaliser(a.icao), normaliser(a.ville), normaliser(a.nom))
            }
            indexEntrees = entrees
            indexSource = tous
            return entrees
        }
    }

    /**
     * Parse les lignes du CSV `airports.csv` (séparateur `;`,
     * en-tête `code;iata;icao;nom;lat;lon;pays;ville;type;regulier`).
     *
     * Fonction pure : elle ignore l'en-tête, tolère les lignes incomplètes ou
     * sans coordonnées exploitables (elle les saute sans lever d'exception) et
     * lit latitude / longitude en `Double`. La colonne `type` n'est pas
     * conservée : elle ne sert à rien pour la v1.
     */
    fun parser(lignes: List<String>): List<Aeroport> {
        val resultat = ArrayList<Aeroport>(lignes.size)
        for (ligneBrute in lignes) {
            // Le fichier vient de Windows (fins de ligne CRLF) : on retire un
            // éventuel `\r` de fin pour ne pas polluer la dernière colonne.
            val ligne = ligneBrute.trimEnd('\r').trim()
            if (ligne.isEmpty()) continue
            val parties = ligne.split(';')
            // En-tête : on repère la ligne des noms de colonnes et on la saute.
            if (parties.isNotEmpty() && parties[0].equals("code", ignoreCase = true)) continue

            // Colonnes : 0=code 1=iata 2=icao 3=nom 4=lat 5=lon 6=pays 7=ville 8=type 9=regulier.
            val code = parties.getOrNull(0)?.trim().orEmpty()
            val iata = parties.getOrNull(1)?.trim().orEmpty()
            val icao = parties.getOrNull(2)?.trim().orEmpty()
            val nom = parties.getOrNull(3)?.trim().orEmpty()
            val lat = parties.getOrNull(4)?.trim()?.toDoubleOrNull()
            val lon = parties.getOrNull(5)?.trim()?.toDoubleOrNull()
            val pays = parties.getOrNull(6)?.trim().orEmpty()
            val ville = parties.getOrNull(7)?.trim().orEmpty()
            val regulier = parties.getOrNull(9)?.trim().equals("yes", ignoreCase = true)

            // Ligne incomplète ou sans coordonnées : inexploitable, on la saute.
            if (lat == null || lon == null) continue

            resultat.add(Aeroport(code, iata, icao, nom, lat, lon, pays, ville, regulier))
        }
        return resultat
    }

    /**
     * Lit la base embarquée depuis `assets/airports.csv` et la met en cache en
     * mémoire. Seul point de contact avec Android : le reste du module reste pur.
     */
    fun charger(contexte: Context): List<Aeroport> {
        cache?.let { return it }
        val lignes = contexte.assets.open("airports.csv")
            .bufferedReader()
            .use { lecteur -> lecteur.readLines() }
        val aeroports = parser(lignes)
        cache = aeroports
        return aeroports
    }

    /**
     * Recherche par code (IATA/OACI), nom d'aéroport ou ville, insensible à la
     * casse et aux accents. Ordre de pertinence (celui du SPEC §6) : code exact,
     * puis préfixe de code, puis code contenu, puis ville, puis nom.
     */
    fun rechercher(tous: List<Aeroport>, texte: String, max: Int = 20): List<Aeroport> {
        val q = normaliser(texte)
        if (q.isEmpty()) return emptyList()

        // L'index normalisé est calculé une seule fois : auparavant, chaque
        // frappe normalisait 5 champs pour 8 925 aéroports, soit ~44 000
        // normalisations et autant de compilations de regex sur le thread
        // principal. C'est ce qui rendait la sélection d'aéroport poussive.
        data class Resultat(val aeroport: Aeroport, val rang: Int, val nom: String)

        val resultats = ArrayList<Resultat>()
        for (e in indexe(tous)) {
            val rang = when {
                e.code == q || e.iata == q || e.icao == q -> 0
                e.code.startsWith(q) || e.iata.startsWith(q) || e.icao.startsWith(q) -> 1
                e.code.contains(q) || e.iata.contains(q) || e.icao.contains(q) -> 2
                e.ville.contains(q) -> 3
                e.nom.contains(q) -> 4
                else -> continue
            }
            resultats.add(Resultat(e.aeroport, rang, e.nom))
        }

        return resultats
            .sortedWith(compareBy({ it.rang }, { it.nom }))
            .take(max)
            .map { it.aeroport }
    }

    /**
     * Aéroport le plus proche d'une position, en privilégiant ceux desservis par
     * un service régulier (utile pour déduire le départ depuis le GPS).
     */
    fun plusProche(
        tous: List<Aeroport>,
        lat: Double,
        lon: Double,
        rayonKm: Double,
    ): Pair<Aeroport, Double>? {
        var meilleur: Pair<Aeroport, Double>? = null
        var meilleurScore = Double.MAX_VALUE
        for (a in tous) {
            val d = Geo.distanceKm(lat, lon, a.lat, a.lon)
            if (d > rayonKm) continue
            // Un aéroport à service régulier passe devant un terrain privé,
            // même un peu plus éloigné.
            val score = if (a.regulier) d else d + PENALITE_NON_REGULIER_KM
            if (score < meilleurScore) {
                meilleurScore = score
                meilleur = a to d
            }
        }
        return meilleur
    }

    /**
     * Aéroports dans un rayon donné autour d'une position, triés du plus proche
     * au plus lointain (distance grand-cercle).
     */
    fun proches(tous: List<Aeroport>, lat: Double, lon: Double, rayonKm: Double): List<Aeroport> {
        return tous
            .mapNotNull { a ->
                val d = Geo.distanceKm(lat, lon, a.lat, a.lon)
                if (d <= rayonKm) a to d else null
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    /**
     * Aéroport exact par son code (IATA ou OACI), insensible à la casse.
     * Retourne `null` si le code est inconnu.
     */
    fun parCode(tous: List<Aeroport>, code: String): Aeroport? {
        val q = normaliser(code)
        if (q.isEmpty()) return null
        return tous.firstOrNull {
            normaliser(it.code) == q || normaliser(it.iata) == q || normaliser(it.icao) == q
        }
    }

    /**
     * Minuscules + suppression des diacritiques : « Aéroport » devient
     * « aeroport », « Égletons » devient « egletons ». Pure JVM (fonctionne en
     * test unitaire comme sur Android).
     */
    private fun normaliser(texte: String): String {
        val minuscules = texte.lowercase(Locale.ROOT)
        val sansAccents = Normalizer.normalize(minuscules, Normalizer.Form.NFD)
            .replace(DIACRITIQUES, "")
        return sansAccents
    }
}
