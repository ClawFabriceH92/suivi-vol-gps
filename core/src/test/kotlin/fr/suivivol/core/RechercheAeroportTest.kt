package fr.suivivol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RechercheAeroportTest {
    private val base = listOf(
        Aeroport("LFPG", "CDG", "Charles de Gaulle International Airport", "Paris (Roissy-en-France)", "FR", 49.00896, 2.554117, 392, true),
        Aeroport("LFPO", "ORY", "Paris-Orly Airport", "Paris (Orly)", "FR", 48.7295, 2.35896, 291, true),
        Aeroport("LFMN", "NCE", "Nice-Côte d'Azur Airport", "Nice", "FR", 43.6584, 7.21587, 12, true),
        Aeroport("RJTT", "HND", "Tokyo Haneda International Airport", "Tokyo", "JP", 35.5523, 139.78, 35, true),
        Aeroport("RJAA", "NRT", "Narita International Airport", "Narita", "JP", 35.7647, 140.386, 141, true),
        Aeroport("EGLL", "LHR", "London Heathrow Airport", "London", "GB", 51.470748, -0.459909, 83, true),
        Aeroport("EGLC", "LCY", "London City Airport", "London", "GB", 51.5053, 0.055278, 19, false),
        Aeroport("LFLC", null, "Clermont-Ferrand Auvergne Airport", "Clermont-Ferrand", "FR", 45.7867, 3.16917, 1090, false),
    )
    private val recherche = RechercheAeroport(base)

    @Test
    fun `code IATA exact en premier`() {
        assertEquals("CDG", recherche.rechercher("cdg").first().code)
        assertEquals("LFPG", recherche.rechercher("lfpg").first().oaci)
    }

    @Test
    fun `prefixe de code puis ville`() {
        val r = recherche.rechercher("L")
        assertEquals("LHR", r[0].code)
        assertEquals("LCY", r[1].code)
    }

    @Test
    fun `ville insensible aux accents et a la casse`() {
        val r = recherche.rechercher("TOKYO")
        assertEquals("HND", r.first().code)
        assertEquals(1, r.size)
        assertEquals("NCE", recherche.rechercher("côte d'azur").first().code)
        assertEquals("NCE", recherche.rechercher("cote d'azur").first().code)
    }

    @Test
    fun `paris donne les deux aeroports parisiens`() {
        val codes = recherche.rechercher("paris").map { it.code }
        assertEquals(listOf("CDG", "ORY"), codes)
    }

    @Test
    fun `aeroport sans IATA utilise le code OACI`() {
        val a = recherche.parCode("LFLC")!!
        assertEquals("LFLC", a.code)
        assertEquals("CDG", recherche.parCode("cdg")!!.code)
    }

    @Test
    fun `libelle`() {
        val a = recherche.parCode("CDG")!!
        assertEquals("CDG · Charles de Gaulle International Airport · France", a.libelle(Locale.FRENCH))
        assertTrue(a.sousLibelle().contains("LFPG"))
    }

    @Test
    fun `requete vide`() {
        assertTrue(recherche.rechercher("  ").isEmpty())
    }
}
