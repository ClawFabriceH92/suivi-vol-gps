package fr.suivivol.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VolDao {
    @Insert
    suspend fun insererVol(vol: VolEntity): Long

    @Update
    suspend fun mettreAJourVol(vol: VolEntity)

    @Query("SELECT * FROM vol WHERE id = :id")
    suspend fun vol(id: Long): VolEntity?

    @Query("SELECT * FROM vol WHERE id = :id")
    fun volFlow(id: Long): Flow<VolEntity?>

    @Query("SELECT * FROM vol ORDER BY decolleLe DESC")
    fun tousLesVols(): Flow<List<VolEntity>>

    @Query("SELECT * FROM vol WHERE termineLe IS NULL ORDER BY decolleLe DESC LIMIT 1")
    suspend fun volActif(): VolEntity?

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM vol")
    suspend fun derniereSequence(): Int

    @Query("DELETE FROM vol WHERE id = :id")
    suspend fun supprimerVol(id: Long)

    @Insert
    suspend fun insererPoint(point: PointTraceEntity)

    @Query("SELECT * FROM point_trace WHERE volId = :volId ORDER BY horodatage")
    suspend fun points(volId: Long): List<PointTraceEntity>

    @Query("SELECT COUNT(*) FROM point_trace WHERE volId = :volId")
    suspend fun nbPoints(volId: Long): Int
}
