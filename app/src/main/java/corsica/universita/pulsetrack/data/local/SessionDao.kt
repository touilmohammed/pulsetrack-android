package corsica.universita.pulsetrack.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Les requêtes, vérifiées à la compilation contre le schéma. */
@Dao
interface SessionDao {

    /** Une requête qui ne se termine jamais : Room la rejoue à chaque écriture dans la table. */
    @Query("SELECT * FROM sessions ORDER BY dateMillis DESC")
    fun all(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionEntity?

    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Upsert
    suspend fun upsertAll(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}
