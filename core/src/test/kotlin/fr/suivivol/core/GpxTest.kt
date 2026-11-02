package fr.suivivol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class GpxTest {
    @Test
    fun `nom de fichier`() {
        // 14 juin 2026 12:00 UTC
        val nom = Gpx.nomFichier("lhr", "JFK", 1_781_438_400_000L, ZoneId.of("UTC"))
        assertEquals("vol_LHR_JFK_2026-06-14.gpx", nom)
    }

    @Test
    fun `gpx contient les points et les aeroports`() {
        val dep = Aeroport("EGLL", "LHR", "London Heathrow Airport", "London", "GB", 51.470748, -0.459909, 83, true)
        val arr = Aeroport("KJFK", "JFK", "John F. Kennedy International Airport", "New York", "US", 40.639447, -73.779317, 13, true)
        val pts = listOf(
            PointTrace(0, 51.47, -0.46, altitudeGpsM = 30.0, vitesseSolKmh = 10.0),
            PointTrace(10_000, 51.48, -0.47, altitudeBaroM = 200.0, altitudeGpsM = 210.0, capDeg = 288.0),
        )
        val gpx = Gpx.generer("LHR → JFK & co", pts, dep, arr)
        assertTrue(gpx.startsWith("<?xml"))
        assertTrue(gpx.contains("<name>LHR → JFK &amp; co</name>"))
        assertTrue(gpx.contains("<wpt lat=\"51.470748\" lon=\"-0.459909\"><name>LHR</name>"))
        assertTrue(gpx.contains("<trkpt lat=\"51.470000\" lon=\"-0.460000\"><ele>30.0</ele><time>1970-01-01T00:00:00Z</time>"))
        assertTrue(gpx.contains("<ele>200.0</ele>"))
        assertTrue(gpx.contains("<cap_deg>288.0</cap_deg>"))
        assertTrue(gpx.trim().endsWith("</gpx>"))
    }
}
