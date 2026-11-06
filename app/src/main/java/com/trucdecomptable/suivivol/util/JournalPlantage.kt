package com.trucdecomptable.suivivol.util

import android.content.Context
import android.content.Intent
import android.os.Build
import com.trucdecomptable.suivivol.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal de plantage LOCAL.
 *
 * Règle du projet : l'application ne collecte rien et n'envoie rien. Ce journal
 * ne quitte donc jamais l'appareil de lui-même : il est écrit sur disque, et
 * c'est l'utilisateur qui décide de le partager depuis l'écran de préparation.
 *
 * Le handler global enregistre la pile d'appels et les dernières actions
 * (étapes de préparation, GPS, carte) : sans câble USB, c'est la seule façon de
 * savoir pourquoi l'application s'est fermée.
 */
object JournalPlantage {

    private const val DOSSIER = "journal"
    private const val FICHIER_PLANTAGE = "dernier_plantage.txt"
    private const val FICHIER_ETAPES = "etapes.txt"
    private const val MAX_ETAPES = 150

    @Volatile
    private var contexte: Context? = null

    @Volatile
    private var installe = false

    fun installer(context: Context) {
        contexte = context.applicationContext
        if (installe) return
        installe = true
        etape("démarrage v${BuildConfig.VERSION_NAME}")

        val precedent = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { fil, erreur ->
            try {
                ecrirePile(erreur)
            } catch (_: Throwable) {
                // ne jamais masquer le plantage d'origine
            }
            precedent?.uncaughtException(fil, erreur)
        }
    }

    /** Note une étape récente (aucune donnée personnelle : ni position ni aéroport). */
    fun etape(libelle: String) {
        val ctx = contexte ?: return
        try {
            val f = fichier(ctx, FICHIER_ETAPES)
            val horodatage = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date())
            val lignes = (if (f.exists()) f.readLines() else emptyList()) + "$horodatage · $libelle"
            f.writeText(lignes.takeLast(MAX_ETAPES).joinToString("\n"))
        } catch (_: Throwable) {
        }
    }

    fun dernierPlantage(context: Context): String? {
        val f = fichier(context, FICHIER_PLANTAGE)
        return if (f.exists() && f.length() > 0) f.readText() else null
    }

    fun effacer(context: Context) {
        fichier(context, FICHIER_PLANTAGE).delete()
        fichier(context, FICHIER_ETAPES).delete()
    }

    /** Partage manuel du journal (chooser Android). */
    fun intentionPartage(context: Context): Intent {
        val plantage = dernierPlantage(context) ?: "Aucun plantage enregistré."
        val etapes = try {
            fichier(context, FICHIER_ETAPES).takeIf { it.exists() }?.readText().orEmpty()
        } catch (_: Throwable) {
            ""
        }
        val contenu = buildString {
            appendLine("Suivi de vol GPS v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine(plantage)
            appendLine()
            appendLine("--- étapes récentes ---")
            append(etapes)
        }
        val envoi = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Suivi de vol — journal de plantage")
            putExtra(Intent.EXTRA_TEXT, contenu)
        }
        return Intent.createChooser(envoi, "Envoyer le journal")
    }

    private fun ecrirePile(erreur: Throwable) {
        val ctx = contexte ?: return
        val horodatage = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date())
        val etapes = try {
            fichier(ctx, FICHIER_ETAPES).takeIf { it.exists() }?.readLines()?.takeLast(25)
                ?.joinToString("\n").orEmpty()
        } catch (_: Throwable) {
            ""
        }
        val contenu = buildString {
            appendLine("=== Plantage du $horodatage ===")
            appendLine("Version : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Appareil : ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine(erreur.stackTraceToString())
            appendLine()
            appendLine("--- étapes récentes ---")
            append(etapes)
        }
        fichier(ctx, FICHIER_PLANTAGE).writeText(contenu)
    }

    private fun fichier(context: Context, nom: String): File {
        val dossier = File(context.filesDir, DOSSIER)
        if (!dossier.exists()) dossier.mkdirs()
        return File(dossier, nom)
    }
}
