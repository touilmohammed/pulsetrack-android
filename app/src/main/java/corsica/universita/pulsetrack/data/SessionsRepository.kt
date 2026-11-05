package corsica.universita.pulsetrack.data

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Le seul endroit qui décide d'où viennent les séances : les écrans ne voient que ce repository.
 * Étape 1 : une liste en mémoire, perdue à la mort du process. L'étape 2 la remplace par Room,
 * sans changer la signature des fonctions.
 */
class SessionsRepository {

    private val store: MutableStateFlow<List<Session>> = MutableStateFlow(EXAMPLES)

    /** Réémet la liste, la plus récente d'abord, à chaque écriture. */
    val sessions: Flow<List<Session>> = store.asSorted()

    fun session(id: String): Flow<Session?> = store.map { list -> list.find { it.id == id } }

    suspend fun get(id: String): Session? = session(id).first()

    suspend fun add(value: ValidSession) {
        val session = value.toSession(id = UUID.randomUUID().toString())
        store.update { it + session }
    }

    suspend fun update(id: String, value: ValidSession) {
        // Une fois modifié, un exemple devient une vraie séance de l'utilisateur.
        val session = value.toSession(id)
        store.update { list -> list.map { if (it.id == id) session else it } }
    }

    suspend fun delete(id: String) {
        store.update { list -> list.filterNot { it.id == id } }
    }

    private fun StateFlow<List<Session>>.asSorted(): Flow<List<Session>> =
        map { list -> list.sortedByDescending { it.dateMillis } }

    private fun ValidSession.toSession(id: String) = Session(
        id = id,
        type = type,
        dateMillis = dateMillis,
        durationMin = durationMin,
        distanceKm = distanceKm,
        note = note,
    )

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
