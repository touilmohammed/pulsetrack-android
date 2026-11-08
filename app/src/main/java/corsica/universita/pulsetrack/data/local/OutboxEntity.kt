package corsica.universita.pulsetrack.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * L'outbox : la file des envois à faire, dans l'ordre (seq). Une opération ne porte pas le contenu
 * de la séance : on envoie toujours la dernière version lue dans sessions.
 * [opId] part en Idempotency-Key : rejouée après une réponse perdue, l'opération n'est pas appliquée deux fois.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val opId: String,
    val sessionId: String,
    val kind: String,
    val createdAt: Long,
)

const val OP_UPSERT = "upsert"
const val OP_DELETE = "delete"

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(op: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY seq LIMIT 1")
    suspend fun next(): OutboxEntity?

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE sessionId = :sessionId")
    suspend fun countFor(sessionId: String): Int

    @Query("SELECT COUNT(*) > 0 FROM outbox WHERE sessionId = :sessionId AND kind = '$OP_DELETE'")
    suspend fun hasPendingDelete(sessionId: String): Boolean

    @Query("DELETE FROM outbox WHERE seq = :seq")
    suspend fun remove(seq: Long)

    @Query("DELETE FROM outbox WHERE sessionId = :sessionId AND kind = '$OP_UPSERT'")
    suspend fun removeUpserts(sessionId: String)
}
