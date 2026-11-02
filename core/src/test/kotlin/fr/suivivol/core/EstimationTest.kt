package fr.suivivol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class EstimationTest {
    @Test
    fun `duree restante a vitesse de croisiere`() {
        val d = Estimation.dureeRestante(1700.0, 850.0)
        assertEquals(2.hours, d)
    }

    @Test
    fun `plancher de vitesse a 200 km h`() {
        assertEquals(1.hours, Estimation.dureeRestante(200.0, 30.0))
        assertEquals(1.hours, Estimation.dureeRestante(200.0, null))
        assertEquals(1.hours, Estimation.dureeRestante(200.0, 0.0))
    }

    @Test
    fun `duree theorique LHR JFK a 850 km h`() {
        val d = Estimation.dureeTheorique(5539.8, 850.0)
        assertEquals("6 h 31 min", Estimation.formatHMin(d))
    }

    @Test
    fun `formatage h min`() {
        assertEquals("35 min", Estimation.formatHMin(35.minutes))
        assertEquals("2 h 05 min", Estimation.formatHMin(125.minutes))
        assertEquals("0 min", Estimation.formatHMin(10.minutes / 60))
        assertEquals("1 h 00 min", Estimation.formatHMin(59.minutes + 40.minutes / 60))
    }

    @Test
    fun `progression bornee`() {
        assertEquals(0.0, Estimation.progression(6000.0, 5000.0), 1e-9)
        assertEquals(1.0, Estimation.progression(-10.0, 5000.0), 1e-9)
        assertEquals(0.5, Estimation.progression(2500.0, 5000.0), 1e-9)
        assertEquals(0.0, Estimation.progression(100.0, 0.0), 1e-9)
    }

    @Test
    fun `detecteur d immobilite apres 5 minutes sans mouvement`() {
        val d = DetecteurImmobilite()
        val t0 = 1_000_000L
        d.signaler(t0, 800.0)
        assertFalse(d.estImmobile(t0 + 4 * 60_000))
        d.signaler(t0 + 60_000, 0.0)
        assertFalse(d.estImmobile(t0 + 5 * 60_000))
        assertTrue(d.estImmobile(t0 + 5 * 60_000 + 1))
        d.signaler(t0 + 6 * 60_000, 300.0)
        assertFalse(d.estImmobile(t0 + 7 * 60_000))
    }

    @Test
    fun `detecteur sans aucun mouvement depuis le debut`() {
        val d = DetecteurImmobilite()
        assertFalse(d.estImmobile(0))
        d.signaler(0, null)
        assertFalse(d.estImmobile(5 * 60_000))
        assertTrue(d.estImmobile(5 * 60_000 + 1))
    }
}
