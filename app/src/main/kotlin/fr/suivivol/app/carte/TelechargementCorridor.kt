package fr.suivivol.app.carte

import android.content.Context
import android.util.Log
import fr.suivivol.core.CorridorTuiles
import fr.suivivol.core.PlanDeVol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.util.MapTileIndex

/**
 * Téléchargement du corridor de carte avant le vol (cahier des charges §4.1).
 * L'état est global : le téléchargement continue si l'utilisateur change d'écran.
 */
object TelechargementCorridor {
    sealed class Etat {
        data object Inactif : Etat()
        data class EnCours(val fait: Int, val total: Int) : Etat()
        data class Termine(val total: Int, val erreurs: Int) : Etat()
        data class Echec(val message: String) : Etat()
    }

    data class Estimation(val nbTuiles: Int, val octets: Long)

    private const val TAG = "Corridor"
    private val _etat = MutableStateFlow<Etat>(Etat.Inactif)
    val etat: StateFlow<Etat> = _etat.asStateFlow()

    private var tache: CacheManager.CacheManagerTask? = null
    private var gestionnaire: CacheManager? = null
    private var ecrivain: SqlTileWriter? = null

    fun estimer(plan: PlanDeVol): Estimation {
        val nb = CorridorTuiles.tuiles(plan.depart.coordonnee, plan.arrivee.coordonnee).size
        return Estimation(nb, CorridorTuiles.estimationOctets(nb))
    }

    /** À appeler depuis le fil principal. */
    fun lancer(context: Context, plan: PlanDeVol) {
        if (_etat.value is Etat.EnCours) return
        val tuiles = CorridorTuiles.tuiles(plan.depart.coordonnee, plan.arrivee.coordonnee)
        val index: List<Long> = tuiles.map { MapTileIndex.getTileIndex(it.z, it.x, it.y) }
        val zoomMin = tuiles.minOfOrNull { it.z } ?: CorridorTuiles.ZOOM_MIN_DEFAUT
        val zoomMax = tuiles.maxOfOrNull { it.z } ?: CorridorTuiles.ZOOM_MAX_DEFAUT
        try {
            val writer = SqlTileWriter()
            val manager = CacheManager(TuilesOsm.SOURCE, writer, 0, 19)
            val task = CacheManager.CacheManagerTask(
                manager,
                manager.getDownloadingAction(context.applicationContext),
                index,
                zoomMin,
                zoomMax,
            )
            task.addCallback(object : CacheManager.CacheManagerCallback {
                private var total = index.size
                override fun onTaskComplete() {
                    _etat.value = Etat.Termine(total, 0)
                    liberer()
                }

                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                    _etat.value = Etat.EnCours(progress.coerceAtMost(total), total)
                }

                override fun downloadStarted() {
                    _etat.value = Etat.EnCours(0, total)
                }

                override fun setPossibleTilesInArea(total: Int) {
                    this.total = total
                }

                override fun onTaskFailed(errors: Int) {
                    _etat.value = Etat.Termine(total, errors)
                    liberer()
                }
            })
            ecrivain = writer
            gestionnaire = manager
            tache = manager.execute(task)
            _etat.value = Etat.EnCours(0, index.size)
        } catch (e: Exception) {
            Log.e(TAG, "Téléchargement impossible", e)
            _etat.value = Etat.Echec(e.message ?: "Téléchargement impossible")
            liberer()
        }
    }

    fun annuler() {
        gestionnaire?.cancelAllJobs()
        _etat.value = Etat.Inactif
        liberer()
    }

    fun reinitialiser() {
        if (_etat.value !is Etat.EnCours) _etat.value = Etat.Inactif
    }

    private fun liberer() {
        tache = null
        gestionnaire = null
        runCatching { ecrivain?.onDetach() }
        ecrivain = null
    }
}
