package com.trucdecomptable.suivivol.vol

/**
 * Logique du mode alterné : on montre les cadrans puis la carte, à tour de
 * rôle, par créneaux de durée fixe. Fonction pure, donc testable sans Android.
 */
object Alternance {

    /**
     * Vrai si les cadrans sont à l'écran au temps [ecouleMs] depuis le début du
     * vol. Créneau pair = cadrans, créneau impair = carte. Une durée invalide
     * (≤ 0) renvoie toujours les cadrans, pour ne jamais clignoter.
     */
    fun cadransAffiches(ecouleMs: Long, dureeSec: Int): Boolean {
        if (dureeSec <= 0) return true
        val creneau = ecouleMs.coerceAtLeast(0L) / (dureeSec * 1000L)
        return creneau % 2L == 0L
    }
}
