package corsica.universita.pulsetrack.data

import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import corsica.universita.pulsetrack.data.local.OP_DELETE
import corsica.universita.pulsetrack.data.local.OP_UPSERT
import corsica.universita.pulsetrack.data.local.OutboxEntity
import corsica.universita.pulsetrack.data.local.PulseTrackDatabase
import corsica.universita.pulsetrack.data.local.SYNC_CONFLICT
import corsica.universita.pulsetrack.data.local.SYNC_ERROR
import corsica.universita.pulsetrack.data.local.SYNC_PENDING
import corsica.universita.pulsetrack.data.local.SYNC_SYNCED
import corsica.universita.pulsetrack.data.local.SessionEntity
import corsica.universita.pulsetrack.data.local.Settings
import corsica.universita.pulsetrack.data.remote.SessionApi
import corsica.universita.pulsetrack.data.remote.SessionDto
import corsica.universita.pulsetrack.data.remote.SessionEnvelope
import corsica.universita.pulsetrack.data.remote.json
import corsica.universita.pulsetrack.work.SyncWorker
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Ce qu'un passage de synchronisation a fait. */
data class SyncReport(val sent: Int, val rejected: Int, val retryLater: Boolean)

/**
 * Le seul endroit qui décide d'où viennent les séances et où elles vont. Room est la source de vérité :
 * l'écran ne lit que la base ; le réseau y écrit, il ne parle jamais à l'écran.
 */
class SessionsRepository(
    private val database: PulseTrackDatabase,
    private val settings: Settings,
    private val api: SessionApi,
    private val workManager: WorkManager,
) {
    private val dao = database.sessionDao()
    private val outbox = database.outboxDao()

    val sessions: Flow<List<Session>> = dao.all().map { list -> list.map { it.toSession() } }

    /** Le badge « n en attente ». */
    val pendingCount: Flow<Int> = dao.pendingCount()

    fun session(id: String): Flow<Session?> = dao.observe(id).map { it?.toSession() }

    suspend fun get(id: String): Session? = dao.get(id)?.toSession()

    // --- Écrire : la séance et son opération d'envoi, dans la même transaction (tout ou rien) ---

    suspend fun add(value: ValidSession) {
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            dao.upsert(value.toEntity(id).copy(syncState = SYNC_PENDING))
            outbox.insert(newOp(id, OP_UPSERT))
        }
        enqueueSync()
    }

    suspend fun update(id: String, value: ValidSession) {
        database.withTransaction {
            val existing = dao.get(id) ?: return@withTransaction
            // Une fois modifié, un exemple devient une vraie séance de l'utilisateur, à envoyer.
            // serverVersion est gardée : c'est elle qui partira en If-Match.
            dao.upsert(value.toEntity(id).copy(syncState = SYNC_PENDING, serverVersion = existing.serverVersion))
            outbox.insert(newOp(id, OP_UPSERT))
        }
        enqueueSync()
    }

    suspend fun delete(id: String) {
        database.withTransaction {
            val existing = dao.get(id) ?: return@withTransaction
            dao.delete(id)
            // Un exemple jamais modifié n'a jamais quitté le téléphone : rien à supprimer sur le serveur.
            if (!existing.isExample) outbox.insert(newOp(id, OP_DELETE))
        }
        enqueueSync()
    }

    /**
     * Confie l'envoi à l'OS. Seule condition : du réseau. Une seule demande « sync » à la fois ;
     * APPEND_OR_REPLACE plutôt que KEEP : une opération ajoutée pendant qu'un envoi tourne
     * relance un passage après lui, au lieu d'attendre le prochain déclencheur.
     */
    fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(SyncWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Au lancement : relancer l'envoi s'il reste des opérations (après une migration, par exemple). */
    suspend fun enqueueSyncIfPending() {
        if (outbox.count() > 0) enqueueSync()
    }

    // --- Envoyer : appelé par SyncWorker, une opération après l'autre, dans l'ordre ---

    /** Une panne réseau lève IOException : le worker réessaiera. */
    suspend fun syncPending(): SyncReport {
        val deviceId = settings.deviceId()
        var sent = 0
        var rejected = 0
        while (true) {
            val op = outbox.next() ?: break
            when (send(op, deviceId)) {
                Outcome.SENT -> sent++
                Outcome.REJECTED -> rejected++
                Outcome.RETRY_LATER -> return SyncReport(sent, rejected, retryLater = true)
            }
        }
        return SyncReport(sent, rejected, retryLater = false)
    }

    private enum class Outcome { SENT, REJECTED, RETRY_LATER }

    private suspend fun send(op: OutboxEntity, deviceId: String): Outcome {
        if (op.kind == OP_DELETE) {
            val response = api.delete(op.sessionId, deviceId, op.opId)
            if (response.code().isTemporary()) return Outcome.RETRY_LATER
            outbox.remove(op.seq) // 204, ou une erreur définitive : dans les deux cas, plus rien à faire
            return if (response.isSuccessful) Outcome.SENT else Outcome.REJECTED
        }

        // La séance a été supprimée depuis : l'opération de suppression qui suit suffit.
        val session = dao.get(op.sessionId) ?: run { outbox.remove(op.seq); return Outcome.SENT }
        val ifMatch = session.serverVersion?.let { "\"$it\"" }
        val response = api.put(session.id, deviceId, op.opId, ifMatch, ifMatch, session.toDto())
        val code = response.code()
        return when {
            response.isSuccessful -> {
                val version = response.body()?.session?.version
                database.withTransaction {
                    outbox.remove(op.seq)
                    // Encore une modification en file pour cette séance ? Elle reste « en attente ».
                    val state = if (outbox.countFor(session.id) == 0) SYNC_SYNCED else SYNC_PENDING
                    dao.setSyncState(session.id, state, version)
                }
                Outcome.SENT
            }
            code == 412 -> {
                // Modifiée ailleurs : on n'écrase rien. La version du serveur est notée ; une nouvelle
                // modification de l'utilisateur partira avec elle (il choisit alors de garder la sienne).
                val serverVersion = response.errorBody()?.string()
                    ?.let { runCatching { json.decodeFromString<SessionEnvelope>(it).session.version }.getOrNull() }
                database.withTransaction {
                    outbox.removeUpserts(session.id)
                    dao.setSyncState(session.id, SYNC_CONFLICT, serverVersion ?: session.serverVersion)
                }
                Outcome.REJECTED
            }
            code.isTemporary() -> Outcome.RETRY_LATER
            else -> {
                // 4xx définitive (séance refusée) : on la signale sans bloquer le reste de la file.
                database.withTransaction {
                    outbox.remove(op.seq)
                    dao.setSyncState(session.id, SYNC_ERROR)
                }
                Outcome.REJECTED
            }
        }
    }

    private fun Int.isTemporary() = this == 408 || this == 429 || this >= 500

    // --- Relire : le réseau rafraîchit Room, qui reste la source de vérité ---

    /**
     * Une séance du serveur ne remplace la copie locale que si celle-ci est synchronisée et plus ancienne :
     * une séance en attente d'envoi n'est jamais écrasée. Même règle que pullSessions() dans la PWA.
     */
    suspend fun refresh() {
        val remote = api.list(settings.deviceId()).sessions
        database.withTransaction {
            for (dto in remote) {
                val version = dto.version ?: continue
                if (outbox.hasPendingDelete(dto.id)) continue
                val local = dao.get(dto.id)
                val newer = local == null || (local.syncState == SYNC_SYNCED && (local.serverVersion ?: 0) < version)
                if (newer) dao.upsert(dto.toEntity())
            }
        }
    }

    // --- Exemples ---

    /**
     * Une seule fois dans la vie de l'app : un exemple supprimé ne doit pas revenir au lancement suivant.
     * Les identifiants fixes rendent l'import sans danger si l'app meurt avant de poser le drapeau.
     */
    suspend fun importExamplesOnce() {
        if (settings.examplesImported()) return
        dao.upsertAll(EXAMPLES.map { it.toEntity() })
        settings.setExamplesImported()
    }

    private fun newOp(sessionId: String, kind: String) =
        OutboxEntity(opId = UUID.randomUUID().toString(), sessionId = sessionId, kind = kind, createdAt = System.currentTimeMillis())

    private companion object {
        private fun millis(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

        // Les trois exemples de la vitrine de la PWA, avec les mêmes identifiants.
        val EXAMPLES = listOf(
            Session(
                id = "seed-1",
                type = SessionType.COURSE,
                dateMillis = millis("2026-10-04T09:30:00+02:00"),
                durationMin = 45,
                distanceKm = 8.2,
                note = "Sortie trail sur le sentier de la Restonica, sans réseau au-dessus du lac.",
                isExample = true,
            ),
            Session(
                id = "seed-2",
                type = SessionType.MUSCULATION,
                dateMillis = millis("2026-10-06T18:00:00+02:00"),
                durationMin = 60,
                distanceKm = null,
                note = "Séance haut du corps en salle au sous-sol : 5 séries de tractions et de développé couché.",
                isExample = true,
            ),
            Session(
                id = "seed-3",
                type = SessionType.VELO,
                dateMillis = millis("2026-10-08T16:15:00+02:00"),
                durationMin = 95,
                distanceKm = 38.0,
                note = "Boucle de Corte par la vallée du Tavignano, retour au crépuscule.",
                isExample = true,
            ),
        )
    }
}

internal fun SessionEntity.toSession() = Session(
    id = id,
    type = SessionType.fromApi(type),
    dateMillis = dateMillis,
    durationMin = durationMin,
    distanceKm = distanceKm,
    note = note,
    isExample = isExample,
    syncState = SyncState.fromStored(syncState),
)

internal fun Session.toEntity() = SessionEntity(
    id = id,
    type = type.apiValue,
    dateMillis = dateMillis,
    durationMin = durationMin,
    distanceKm = distanceKm,
    note = note,
    isExample = isExample,
    syncState = syncState.stored,
)

internal fun ValidSession.toEntity(id: String) = SessionEntity(
    id = id,
    type = type.apiValue,
    dateMillis = dateMillis,
    durationMin = durationMin,
    distanceKm = distanceKm,
    note = note,
    isExample = false,
)

internal fun SessionEntity.toDto() = SessionDto(
    id = id,
    type = type,
    date = Instant.ofEpochMilli(dateMillis).toString(),
    durationMin = durationMin,
    distanceKm = distanceKm,
    note = note,
)

internal fun SessionDto.toEntity() = SessionEntity(
    id = id,
    type = type,
    dateMillis = Instant.parse(date).toEpochMilli(),
    durationMin = durationMin,
    distanceKm = distanceKm,
    note = note,
    isExample = false,
    syncState = SYNC_SYNCED,
    serverVersion = version,
)
