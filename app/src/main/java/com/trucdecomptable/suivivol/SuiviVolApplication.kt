package com.trucdecomptable.suivivol

import android.app.Application
import com.trucdecomptable.suivivol.util.JournalPlantage

/**
 * Application déclarée pour installer le journal de plantage avant toute
 * activité : un plantage au démarrage laisse ainsi une trace exploitable.
 */
class SuiviVolApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        JournalPlantage.installer(this)
    }
}
