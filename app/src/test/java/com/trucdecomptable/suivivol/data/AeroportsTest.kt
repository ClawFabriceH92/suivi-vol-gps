package com.trucdecomptable.suivivol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie le parseur et la recherche de la base d'aéroports sur un petit jeu de
 * lignes synthétiques (le vrai fichier `airports.csv` est embarqué et lu via le
 * Context, ce qui ne se teste pas en JUnit pur).
 */
class AeroportsTest {

    private val lignes = listOf(
        "code;iata;icao;nom;lat;lon;pays;ville;type;regulier",
        "CDG;CDG;LFPG;Charles de Gaulle International Airport;49.00896;2.554117;FR;Paris;large_airport;yes",
        "ORY;ORY;LFPO;Orly Airport;48.723333;2.379444;FR;Paris;large_airport;yes",
        "JFK;JFK;KJFK;John F. Kennedy International Airport;40.639447;-73.779317;US;New York;large_airport;yes",
        "NCE;NCE;LFMN;Aéroport de Nice Côte d'Azur;43.6584;7.2159;FR;Nice;large_airport;yes",
        "LBG;LBG;LFPB;Paris Le Bourget;48.9694;2.4414;FR;Paris;medium_airport;no",
        "ZZZ;;;Ligne sans longitude;10.0;;FR;;small_airport;yes", // longitude vide → sautée
        "TST;;;Ligne trop courte", // moins de 10 colonnes → sautée
    )

    private val aeroports = Aeroports.parser(lignes)

    @Test
    fun parserIgnoreLenTeteEtSauteLesLignesIncompletes() {
        // 5 lignes valides sur 6 (l'en-tête et les deux lignes incomplètes sont ignorées).
        assertEquals(5, aeroports.size)
    }

    @Test
    fun parserLitLesChampsEtLesCoordonneesEnDouble() {
        val cdg = aeroports.first { it.code == "CDG" }
        assertEquals("CDG", cdg.iata)
        assertEquals("LFPG", cdg.icao)
        assertEquals("Charles de Gaulle International Airport", cdg.nom)
        assertEquals(49.00896, cdg.lat, 1e-9)
        assertEquals(2.554117, cdg.lon, 1e-9)
        assertEquals("FR", cdg.pays)
        assertEquals("Paris", cdg.ville)
        assertTrue(cdg.regulier)
    }

    @Test
    fun parserConserveLesAccentsEtLitLeServiceRegulier() {
        val nce = aeroports.first { it.code == "NCE" }
        assertEquals("Aéroport de Nice Côte d'Azur", nce.nom)
        assertTrue(nce.regulier)

        // LBG a `regulier = no`.
        val lbg = aeroports.first { it.code == "LBG" }
        assertTrue(!lbg.regulier)
    }

    @Test
    fun rechercheParCodeExactInsensibleALaCasse() {
        val resultat = Aeroports.rechercher(aeroports, "cdg")
        assertEquals("CDG", resultat.first().code)
        // L'OACI seul doit aussi être trouvé.
        assertEquals("CDG", Aeroports.rechercher(aeroports, "lfpg").first().code)
    }

    @Test
    fun rechercheParVille() {
        val codes = Aeroports.rechercher(aeroports, "paris").map { it.code }
        assertTrue("Paris doit ramener CDG, ORY et LBG, obtenu $codes",
            codes.containsAll(listOf("CDG", "ORY", "LBG")))
    }

    @Test
    fun rechercheParNomInsensibleAuxAccents() {
        val resultat = Aeroports.rechercher(aeroports, "aeroport de nice")
        assertEquals("NCE", resultat.first().code)
        // La requête accentuée doit donner le même résultat.
        assertEquals("NCE", Aeroports.rechercher(aeroports, "AÉROPORT DE NICE").first().code)
    }

    @Test
    fun prochesDansUnRayon() {
        val cdg = aeroports.first { it.code == "CDG" }
        val proches = Aeroports.proches(aeroports, cdg.lat, cdg.lon, 50.0)
        val codes = proches.map { it.code }
        // Dans 50 km autour de CDG : CDG, Le Bourget (LBG) et Orly (ORY), mais pas JFK/NCE.
        assertEquals(3, proches.size)
        assertTrue(codes.containsAll(listOf("CDG", "LBG", "ORY")))
        assertEquals("CDG", proches.first().code) // le plus proche d'abord
    }

    @Test
    fun parCodeConnuEtInconnu() {
        assertEquals("CDG", Aeroports.parCode(aeroports, "LFPG")?.code)
        assertEquals("CDG", Aeroports.parCode(aeroports, "cdg")?.code)
        assertEquals("JFK", Aeroports.parCode(aeroports, "jfk")?.code)
        assertNull(Aeroports.parCode(aeroports, "ZZZ"))
    }

    @Test
    fun `plusProche prefere un aeroport a service regulier`() {
        // Position au Bourget (LBG, sans service régulier), CDG à ~8 km.
        val trouve = Aeroports.plusProche(aeroports, 48.9694, 2.4414, 40.0)
        assertTrue("un aéroport doit être trouvé", trouve != null)
        assertEquals("CDG", trouve!!.first.code)
        assertTrue("la distance doit être plausible", trouve.second > 0.0)
    }

    @Test
    fun `plusProche respecte le rayon`() {
        // Au large de Nice, à plus de 40 km de tout : rien.
        assertNull(Aeroports.plusProche(aeroports, 42.5, 7.2159, 40.0))
        // À 200 km de rayon, Nice ressort.
        assertEquals("NCE", Aeroports.plusProche(aeroports, 42.5, 7.2159, 200.0)?.first?.code)
    }

    @Test
    fun `recherche repetee donne le meme resultat avec l index en cache`() {
        // L'index normalisé est calculé une fois puis réutilisé : les résultats
        // doivent rester identiques d'un appel à l'autre (non-régression perf).
        val premier = Aeroports.rechercher(aeroports, "paris")
        val second = Aeroports.rechercher(aeroports, "paris")
        assertEquals(premier.map { it.code }, second.map { it.code })
        assertEquals("CDG", premier.first().code)
    }
}
