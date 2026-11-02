package fr.suivivol.app.data

import android.app.Application
import fr.suivivol.app.data.db.AppDatabase

/**
 * Point d'accès unique aux dépendances de l'application (pas de framework d'injection :
 * l'application est petite et n'a qu'une activité et un service).
 */
object Graphe {
    lateinit var application: Application
        private set

    val db: AppDatabase by lazy { AppDatabase.creer(application) }
    val aeroports: AeroportRepository by lazy { AeroportRepository(application) }
    val reglages: ReglagesRepository by lazy { ReglagesRepository(application) }

    fun initialiser(app: Application) {
        application = app
    }
}
