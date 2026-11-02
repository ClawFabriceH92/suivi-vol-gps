package fr.suivivol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LissageTest {
    @Test
    fun `aucune valeur donne null`() {
        assertNull(LisseurVitesse().vitesseLissee)
    }

    @Test
    fun `mediane des 5 dernieres vitesses`() {
        val l = LisseurVitesse(5)
        listOf(800.0, 810.0, 5000.0, 820.0, 815.0).forEach { l.ajouter(it) }
        assertEquals(815.0, l.vitesseLissee!!, 1e-9)
        // la fenêtre glisse : 800 sort, 830 entre -> 810 815 820 830 5000
        l.ajouter(830.0)
        assertEquals(820.0, l.vitesseLissee!!, 1e-9)
    }

    @Test
    fun `vitesses nulles ignorees`() {
        val l = LisseurVitesse()
        l.ajouter(0.0)
        l.ajouter(null)
        l.ajouter(-5.0)
        assertNull(l.vitesseLissee)
        l.ajouter(100.0)
        assertEquals(100.0, l.vitesseLissee!!, 1e-9)
    }

    @Test
    fun `mediane paire`() {
        assertEquals(15.0, LisseurVitesse.mediane(listOf(10.0, 20.0))!!, 1e-9)
    }
}
