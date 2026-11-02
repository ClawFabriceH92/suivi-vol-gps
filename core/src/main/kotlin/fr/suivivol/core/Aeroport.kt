package fr.suivivol.core

import java.text.Normalizer
import java.util.Locale

/** Aéroport de la base embarquée (source OurAirports). */
data class Aeroport(
    val oaci: String,
    val iata: String?,
    val nom: String,
    val ville: String?,
    val paysIso: String,
    val lat: Double,
    val lon: Double,
    val altitudeFt: Int?,
    val grand: Boolean = false,
) {
    /** Code court préféré : IATA s'il existe, sinon OACI. */
    val code: String get() = iata?.takeIf { it.isNotBlank() } ?: oaci

    val coordonnee: Coordonnee get() = Coordonnee(lat, lon)

    val altitudeM: Double? get() = altitudeFt?.let { it * 0.3048 }

    /** Nom du pays dans la langue du téléphone, à partir du code ISO. */
    fun nomPays(locale: Locale = Locale.getDefault()): String =
        runCatching { Locale("", paysIso).getDisplayCountry(locale) }.getOrNull()?.takeIf { it.isNotBlank() } ?: paysIso

    /** « CDG · Paris Charles de Gaulle · France ». */
    fun libelle(locale: Locale = Locale.getDefault()): String = "$code · $nom · ${nomPays(locale)}"

    /** « Paris · LFPG » (ligne secondaire). */
    fun sousLibelle(): String = listOfNotNull(ville?.takeIf { it.isNotBlank() }, oaci.takeIf { it != code }).joinToString(" · ")
}

/**
 * Recherche hors ligne dans la base d'aéroports (cahier des charges §6) :
 * insensible à la casse et aux accents ; priorité au code exact (IATA puis OACI), puis au
 * préfixe de code, puis au nom de ville, puis au nom d'aéroport.
 */
class RechercheAeroport(aeroports: List<Aeroport>) {
    private class Entree(val aeroport: Aeroport) {
        val iata = aeroport.iata?.let { normaliser(it) } ?: ""
        val oaci = normaliser(aeroport.oaci)
        val ville = aeroport.ville?.let { normaliser(it) } ?: ""
        val nom = normaliser(aeroport.nom)
    }

    private val entrees = aeroports.map { Entree(it) }
    private val parCode: Map<String, Aeroport> = buildMap {
        for (a in aeroports) {
            put(normaliser(a.oaci), a)
            a.iata?.takeIf { it.isNotBlank() }?.let { putIfAbsent(normaliser(it), a) }
        }
    }

    val taille: Int get() = entrees.size

    fun parCode(code: String): Aeroport? = parCode[normaliser(code)]

    fun rechercher(requete: String, max: Int = 20): List<Aeroport> {
        val q = normaliser(requete)
        if (q.isEmpty()) return emptyList()
        val scores = ArrayList<Pair<Int, Entree>>()
        for (e in entrees) {
            val score = when {
                e.iata == q -> 0
                e.oaci == q -> 1
                e.iata.startsWith(q) -> 2
                e.oaci.startsWith(q) -> 3
                e.ville.startsWith(q) -> 4
                motCommencePar(e.ville, q) -> 5
                e.nom.startsWith(q) -> 6
                motCommencePar(e.nom, q) -> 7
                e.ville.contains(q) -> 8
                e.nom.contains(q) -> 9
                else -> -1
            }
            if (score >= 0) scores.add(score to e)
        }
        return scores
            .sortedWith(compareBy<Pair<Int, Entree>> { it.first }.thenBy { !it.second.aeroport.grand }.thenBy { it.second.nom })
            .take(max)
            .map { it.second.aeroport }
    }

    private fun motCommencePar(texte: String, prefixe: String): Boolean =
        texte.split(' ', '-', '/', '(', ',').any { it.isNotEmpty() && it.startsWith(prefixe) }

    companion object {
        /** Minuscules, sans accents, espaces multiples réduits. */
        fun normaliser(s: String): String {
            val decompose = Normalizer.normalize(s, Normalizer.Form.NFD)
            val sansAccents = decompose.replace(Regex("\\p{M}+"), "")
            return sansAccents.lowercase(Locale.ROOT).replace('’', '\'').replace(Regex("\\s+"), " ").trim()
        }
    }
}
