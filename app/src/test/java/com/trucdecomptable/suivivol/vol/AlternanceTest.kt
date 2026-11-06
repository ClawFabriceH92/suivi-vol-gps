package com.trucdecomptable.suivivol.vol

import com.trucdecomptable.suivivol.data.ModeAffichage
import com.trucdecomptable.suivivol.data.ReglagesVol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mode alterné de l'écran de vol et valeurs par défaut des réglages. */
class AlternanceTest {

    @Test
    fun `cadrans puis carte par creneaux de duree egale`() {
        val duree = 10 // secondes
        assertTrue(Alternance.cadransAffiches(0, duree))
        assertTrue(Alternance.cadransAffiches(9_999, duree))
        assertFalse(Alternance.cadransAffiches(10_000, duree))
        assertFalse(Alternance.cadransAffiches(19_999, duree))
        assertTrue(Alternance.cadransAffiches(20_000, duree))
        assertFalse(Alternance.cadransAffiches(59_000, duree)) // créneau 5 : carte
        assertTrue(Alternance.cadransAffiches(60_000, duree))  // créneau 6 : cadrans
    }

    @Test
    fun `duree invalide affiche toujours les cadrans sans clignoter`() {
        assertTrue(Alternance.cadransAffiches(0, 0))
        assertTrue(Alternance.cadransAffiches(99_000, -5))
    }

    @Test
    fun `temps negatif ne casse pas l alternance`() {
        assertTrue(Alternance.cadransAffiches(-3_000, 10))
    }

    @Test
    fun `reglages par defaut carte derriere les cadrans`() {
        val r = ReglagesVol()
        assertEquals(ModeAffichage.DEUX, r.mode)
        assertFalse(r.alterne)
        assertEquals(10, r.dureeAlternanceSec)
        assertEquals(85, r.transparenceCadrans)
    }
}
