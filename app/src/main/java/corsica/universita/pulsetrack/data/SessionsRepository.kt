package corsica.universita.pulsetrack.data

import corsica.universita.pulsetrack.data.local.SessionDao
import corsica.universita.pulsetrack.data.local.SessionEntity
import corsica.universita.pulsetrack.data.local.Settings
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Le seul endroit qui décide d'où viennent les séances. Room est la source de vérité :
 * les écrans écoutent la base, et toute écriture réémet la liste sans code de rafraîchissement.
 */
class SessionsRepository(
    private val dao: SessionDao,
    private val settings: Settings,
) {

    val sessions: Flow<List<Session>> = dao.all().map { list -> list.map { it.toSession() } }

    fun session(id: String): Flow<Session?> = dao.observe(id).map { it?.toSession() }

    suspend fun get(id: String): Session? = dao.get(id)?.toSession()

    suspend fun add(value: ValidSession) {
        dao.upsert(value.toEntity(id = UUID.randomUUID().toString()))
    }

    suspend fun update(id: String, value: ValidSession) {
        // Une fois modifié, un exemple devient une vraie séance de l'utilisateur.
        dao.upsert(value.toEntity(id))
    }

    suspend fun delete(id: String) {
        dao.delete(id)
    }

    /**
     * Une seule fois dans la vie de l'app : un exemple supprimé ne doit pas revenir au lancement suivant.
     * Les identifiants fixes rendent l'import sans danger si l'app meurt avant de poser le drapeau.
     */
    suspend fun importExamplesOnce() {
        if (settings.examplesImported()) return
        dao.upsertAll(EXAMPLES.map { it.toEntity() })
        settings.setExamplesImported()
    }

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
)

internal fun Session.toEntity() = SessionEntity(
    id = id,
    type = type.apiValue,
    dateMillis = dateMillis,
    durationMin = durationMin,
    distanceKm = distanceKm,
    note = note,
    isExample = isExample,
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
