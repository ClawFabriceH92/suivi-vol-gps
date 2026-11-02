package fr.suivivol.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VolEntity::class, PointTraceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun volDao(): VolDao

    companion object {
        fun creer(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "suivi_vol.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
