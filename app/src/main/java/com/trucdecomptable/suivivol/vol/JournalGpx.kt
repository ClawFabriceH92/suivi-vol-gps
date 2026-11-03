package com.trucdecomptable.suivivol.vol

import java.time.Instant
import java.time.LocalDate

/**
 * Construit un journal GPX 1.1 (format d'échange de traces) en [String], sans
 * aucune écriture disque : c'est à l'appelant (ou à l'écran de partage) de
 * décider quoi faire de la chaîne. Le code reste pur pour être testable en
 * JUnit.
 */
object JournalGpx {

    /** Créateur déclaré dans l'en-tête du document GPX. */
    const val CREATEUR = "Suivi de vol GPS"

    /**
     * Construit le document GPX complet : en-tête XML, élément `gpx` (version
     * et créateur, espace de noms GPX 1.1), un `trk` contenant un unique
     * `trkseg`, puis un `trkpt` par point portant `lat`/`lon` et contenant un
     * `ele` (altitude en mètres) et un `time` (ISO 8601 UTC).
     */
    fun construire(points: List<PointVol>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"").append(CREATEUR)
            .append("\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk>\n    <trkseg>\n")
        for (p in points) {
            sb.append("      <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">\n")
            sb.append("        <ele>").append(p.altitudeM).append("</ele>\n")
            sb.append("        <time>").append(formatIso(p.horodatageMs)).append("</time>\n")
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n  </trk>\n</gpx>\n")
        return sb.toString()
    }

    /**
     * Nom de fichier proposé pour l'export, daté au format `AAAA-MM-JJ` :
     * « suivi-vol-2026-11-03.gpx ».
     */
    fun nomFichier(date: LocalDate): String = "suivi-vol-$date.gpx"

    /**
     * Horodatage epoch (ms) vers ISO 8601 UTC (« 2026-11-03T14:05:00Z »).
     */
    private fun formatIso(horodatageMs: Long): String = Instant.ofEpochMilli(horodatageMs).toString()
}
