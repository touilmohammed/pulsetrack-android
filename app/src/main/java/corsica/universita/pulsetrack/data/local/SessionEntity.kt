package corsica.universita.pulsetrack.data.local

import androidx.room.ColumnInfo
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
    // Version 2 : où en est l'envoi. Même valeur par défaut ici et dans MIGRATION_1_2.
    @ColumnInfo(defaultValue = "'synced'") val syncState: String = SYNC_SYNCED,
    /** Version connue du serveur, envoyée en If-Match ; nulle tant que la séance n'y est jamais arrivée. */
    val serverVersion: Int? = null,
    // Version 3 : la position (envoyée au serveur) et le chemin de la photo (qui reste sur le téléphone).
    val lat: Double? = null,
    val lng: Double? = null,
    /** Rayon de précision, en mètres. */
    val accuracy: Int? = null,
    /** Un chemin dans filesDir/photos : les octets ne vont jamais dans la base. */
    val photoPath: String? = null,
)

const val SYNC_SYNCED = "synced"
const val SYNC_PENDING = "pending"
const val SYNC_ERROR = "error"
const val SYNC_CONFLICT = "conflict"
