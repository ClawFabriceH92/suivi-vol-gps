package com.trucdecomptable.suivivol.util

import android.util.Log

/**
 * Journal local des incidents techniques (mise à jour, GPS, carte). On écrit
 * dans logcat plutôt que dans un fichier : l'app ne collecte rien et n'envoie
 * rien, ce qui est la règle du projet.
 */
object Diag {
    private const val TAG = "SuiviVol"

    fun record(categorie: String, message: String) {
        Log.w(TAG, "[$categorie] $message")
    }

    fun record(categorie: String, erreur: Throwable) =
        record(categorie, "${erreur.javaClass.simpleName}: ${erreur.message.orEmpty()}")
}
