package fr.suivivol.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Génération d'un fichier GPX 1.1 à partir de la trace. */
object Gpx {
    private const val CREATEUR = "SuiviVolGPS"

    /** Exemple : « vol_LHR_JFK_2026-06-14.gpx ». */
    fun nomFichier(codeDepart: String, codeArrivee: String, decolleLeMs: Long, fuseau: ZoneId = ZoneId.systemDefault()): String {
        val date = LocalDate.ofInstant(Instant.ofEpochMilli(decolleLeMs), fuseau)
        return "vol_${nettoyer(codeDepart)}_${nettoyer(codeArrivee)}_$date.gpx"
    }

    private fun nettoyer(code: String) = code.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }.ifEmpty { "XXX" }

    fun generer(
        nom: String,
        points: List<PointTrace>,
        depart: Aeroport? = null,
        arrivee: Aeroport? = null,
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"$CREATEUR\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <metadata><name>").append(echapper(nom)).append("</name></metadata>\n")
        for (a in listOfNotNull(depart, arrivee)) {
            sb.append("  <wpt lat=\"").append(fmt(a.lat)).append("\" lon=\"").append(fmt(a.lon)).append("\">")
            sb.append("<name>").append(echapper(a.code)).append("</name>")
            sb.append("<desc>").append(echapper(a.nom)).append("</desc></wpt>\n")
        }
        sb.append("  <trk>\n    <name>").append(echapper(nom)).append("</name>\n    <trkseg>\n")
        var precedent: PointTrace? = null
        for (p in points.sortedBy { it.horodatageMs }) {
            val prev = precedent
            if (prev != null && !AnalyseTrace.segmentValide(prev, p)) {
                sb.append("    </trkseg>\n    <trkseg>\n")
            }
            sb.append("      <trkpt lat=\"").append(fmt(p.lat)).append("\" lon=\"").append(fmt(p.lon)).append("\">")
            p.altitudeAfficheeM?.let { sb.append("<ele>").append(fmt(it, 1)).append("</ele>") }
            sb.append("<time>").append(Instant.ofEpochMilli(p.horodatageMs)).append("</time>")
            val extensions = buildList {
                p.vitesseSolKmh?.let { add("<vitesse_kmh>${fmt(it, 1)}</vitesse_kmh>") }
                p.capDeg?.let { add("<cap_deg>${fmt(it, 1)}</cap_deg>") }
                p.altitudeGpsM?.let { add("<altitude_gps_m>${fmt(it, 1)}</altitude_gps_m>") }
                p.altitudeBaroM?.let { add("<altitude_baro_m>${fmt(it, 1)}</altitude_baro_m>") }
                p.precisionM?.let { add("<precision_m>${fmt(it, 1)}</precision_m>") }
            }
            if (extensions.isNotEmpty()) sb.append("<extensions>").append(extensions.joinToString("")).append("</extensions>")
            sb.append("</trkpt>\n")
            precedent = p
        }
        sb.append("    </trkseg>\n  </trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun fmt(v: Double, decimales: Int = 6): String = String.format(Locale.ROOT, "%.${decimales}f", v)

    private fun echapper(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
