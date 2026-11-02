package fr.suivivol.core

/**
 * Vitesse sol lissée : médiane des `taille` dernières vitesses GPS non nulles (cahier des charges §7.4).
 */
class LisseurVitesse(private val taille: Int = 5) {
    private val valeurs = ArrayDeque<Double>()

    /** Ajoute une vitesse en km/h. Les valeurs nulles ou négatives sont ignorées. */
    fun ajouter(vitesseKmh: Double?) {
        if (vitesseKmh == null || vitesseKmh <= 0.0 || vitesseKmh.isNaN()) return
        valeurs.addLast(vitesseKmh)
        while (valeurs.size > taille) valeurs.removeFirst()
    }

    /** Médiane glissante, ou null si aucune vitesse valide n'a encore été reçue. */
    val vitesseLissee: Double?
        get() = mediane(valeurs)

    fun reinitialiser() = valeurs.clear()

    companion object {
        fun mediane(valeurs: Collection<Double>): Double? {
            if (valeurs.isEmpty()) return null
            val triees = valeurs.sorted()
            val n = triees.size
            return if (n % 2 == 1) triees[n / 2] else (triees[n / 2 - 1] + triees[n / 2]) / 2.0
        }
    }
}
