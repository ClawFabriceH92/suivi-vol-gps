package fr.suivivol.app.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import fr.suivivol.app.BuildConfig
import fr.suivivol.core.Aeroport
import fr.suivivol.core.Gpx
import fr.suivivol.core.PointTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Export GPX par le partage Android (cahier des charges §4.3). */
object ExportGpx {
    suspend fun partager(
        context: Context,
        codeDepart: String,
        codeArrivee: String,
        decolleLeMs: Long,
        points: List<PointTrace>,
        depart: Aeroport?,
        arrivee: Aeroport?,
    ) {
        val nomFichier = Gpx.nomFichier(codeDepart, codeArrivee, decolleLeMs)
        val fichier = withContext(Dispatchers.IO) {
            val dossier = File(context.cacheDir, "exports").apply { mkdirs() }
            val f = File(dossier, nomFichier)
            f.writeText(Gpx.generer("Vol $codeDepart → $codeArrivee", points, depart, arrivee), Charsets.UTF_8)
            f
        }
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fichiers", fichier)
        val envoi = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, nomFichier)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val selecteur = Intent.createChooser(envoi, "Partager la trace GPX").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(selecteur)
    }
}
