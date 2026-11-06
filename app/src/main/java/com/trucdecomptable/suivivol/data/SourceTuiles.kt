package com.trucdecomptable.suivivol.data

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * Fond de carte OpenStreetMap sans clé, avec une politique de tuiles qui
 * AUTORISE le téléchargement en masse.
 *
 * Pourquoi ce fichier existe : la source par défaut d'osmdroid (MAPNIK) porte le
 * drapeau FLAG_NO_BULK. Le bouton « Préparer la carte » lançait donc le
 * CacheManager, qui levait TileSourcePolicyException depuis une AsyncTask.
 * Cette exception ne peut pas être interceptée par l'appelant : elle remonte au
 * thread principal et ferme l'application (constaté sur Android 14, pile
 * `CacheManager$3.preCheck`). D'où une source dédiée, explicite.
 */
object SourceTuiles {

    /**
     * 2 requêtes simultanées au maximum, aucun drapeau d'interdiction.
     * La politique d'usage d'OpenStreetMap proscrit le moissonnage : le nombre
     * de tuiles reste donc plafonné par [TUILLES_MAX] avant tout lancement.
     */
    val OSM: ITileSource = XYTileSource(
        "osm-sans-cle",
        0,
        19,
        256,
        ".png",
        arrayOf(
            "https://a.tile.openstreetmap.org/",
            "https://b.tile.openstreetmap.org/",
            "https://c.tile.openstreetmap.org/",
        ),
        "© OpenStreetMap contributors",
        TileSourcePolicy(2, 0),
    )

    /** Plafond de tuiles téléchargées en une fois (usage raisonnable du serveur). */
    const val TUILLES_MAX = 600
}
