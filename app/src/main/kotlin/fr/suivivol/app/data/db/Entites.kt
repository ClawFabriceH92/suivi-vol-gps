package fr.suivivol.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.suivivol.core.PointTrace

/** Table `vol` (cahier des charges §5). */
@Entity(tableName = "vol")
data class VolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val aeroportDepart: String,
    val aeroportArrivee: String,
    val decolleLe: Long,
    val termineLe: Long? = null,
    val distanceTheoriqueKm: Double,
    val sequence: Int,
)

/** Table `point_trace` (cahier des charges §5). */
@Entity(
    tableName = "point_trace",
    foreignKeys = [
        ForeignKey(
            entity = VolEntity::class,
            parentColumns = ["id"],
            childColumns = ["volId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("volId")],
)
data class PointTraceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val volId: Long,
    val horodatage: Long,
    val lat: Double,
    val lon: Double,
    val altitudeGpsM: Double?,
    val altitudeBaroM: Double?,
    val vitesseSolKmh: Double?,
    val capDeg: Double?,
    val precisionM: Double?,
) {
    fun versCore(): PointTrace = PointTrace(
        horodatageMs = horodatage,
        lat = lat,
        lon = lon,
        altitudeGpsM = altitudeGpsM,
        altitudeBaroM = altitudeBaroM,
        vitesseSolKmh = vitesseSolKmh,
        capDeg = capDeg,
        precisionM = precisionM,
    )
}
