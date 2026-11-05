package corsica.universita.pulsetrack.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Une ligne de la table sessions : le format du disque, distinct de Session (le format de l'écran).
 * Chacun peut évoluer seul ; le repository convertit de l'un à l'autre.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val dateMillis: Long,
    val durationMin: Int,
    val distanceKm: Double?,
    val note: String,
    val isExample: Boolean,
)
