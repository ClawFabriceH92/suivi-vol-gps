package fr.suivivol.app.carte

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import fr.suivivol.app.BuildConfig
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import java.io.File

/**
 * Configuration d'osmdroid et source de tuiles OpenStreetMap.
 *
 * Politique d'usage des tuiles OSM : User-Agent identifiant l'application, deux téléchargements
 * simultanés au plus, pas de préchargement autour de la vue. Le seul téléchargement groupé est
 * celui du corridor de vol (zooms 3 à 8, quelques centaines de tuiles), déclenché par l'utilisateur.
 */
object TuilesOsm {
    const val ATTRIBUTION = "© OpenStreetMap contributors"

    val SOURCE: OnlineTileSourceBase = XYTileSource(
        "OSM_SuiviVol",
        0,
        19,
        256,
        ".png",
        arrayOf("https://tile.openstreetmap.org/"),
        ATTRIBUTION,
        TileSourcePolicy(
            2,
            TileSourcePolicy.FLAG_NO_PREVENTIVE or
                TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or
                TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
        ),
    )

    fun configurer(context: Context) {
        val conf = Configuration.getInstance()
        conf.userAgentValue = "SuiviVolGPS/${BuildConfig.VERSION_NAME} (Android; github.com/${BuildConfig.DEPOT_GITHUB})"
        val base = File(context.filesDir, "osmdroid")
        conf.osmdroidBasePath = base
        conf.osmdroidTileCache = File(base, "tuiles")
        conf.tileFileSystemCacheMaxBytes = 1024L * 1024L * 1024L
        conf.tileFileSystemCacheTrimBytes = 900L * 1024L * 1024L
        // Les tuiles en cache restent valables un an : en vol, pas de réseau pour les rafraîchir.
        conf.expirationOverrideDuration = 365L * 24L * 3600L * 1000L
        conf.tileDownloadThreads = 2.toShort()
        conf.tileFileSystemThreads = 4.toShort()
        conf.isMapViewHardwareAccelerated = true
    }

    /** Taille actuelle du cache de tuiles en octets. */
    fun tailleCacheOctets(): Long = runCatching { SqlTileWriter().avec { it.size } }.getOrDefault(0L)

    /** Vide entièrement le cache de tuiles. */
    fun viderCache(): Boolean = runCatching { SqlTileWriter().avec { it.purgeCache() } }.getOrDefault(false)

    private inline fun <T> SqlTileWriter.avec(bloc: (SqlTileWriter) -> T): T {
        try {
            return bloc(this)
        } finally {
            onDetach()
        }
    }

    /**
     * Image affichée à la place d'une tuile absente du cache : fond uni et message explicite
     * (cahier des charges §4.2 : jamais un écran blanc muet).
     */
    fun imageTuileAbsente(context: Context): Drawable {
        val taille = 256
        val bitmap = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(222, 228, 236))
        val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 100, 115)
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("carte non téléchargée", taille / 2f, taille / 2f - 8f, pinceau)
        canvas.drawText("pour cette zone", taille / 2f, taille / 2f + 20f, pinceau)
        return BitmapDrawable(context.resources, bitmap)
    }
}
