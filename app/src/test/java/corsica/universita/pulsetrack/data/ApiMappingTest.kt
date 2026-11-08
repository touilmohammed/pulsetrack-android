package corsica.universita.pulsetrack.data

import corsica.universita.pulsetrack.data.local.SYNC_SYNCED
import corsica.universita.pulsetrack.data.local.SessionEntity
import corsica.universita.pulsetrack.data.remote.SessionEnvelope
import corsica.universita.pulsetrack.data.remote.SessionList
import corsica.universita.pulsetrack.data.remote.json
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ApiMappingTest {

    private val entity = SessionEntity(
        id = "a",
        type = "velo",
        dateMillis = Instant.parse("2026-10-08T14:15:00Z").toEpochMilli(),
        durationMin = 95,
        distanceKm = 38.0,
        note = "Corte",
        isExample = false,
        serverVersion = 3,
    )

    @Test
    fun `la date part au format ISO, comme toISOString`() {
        assertEquals("2026-10-08T14:15:00Z", entity.toDto().date)
    }

    @Test
    fun `le corps envoye ne contient ni version ni nulls`() {
        val body = json.encodeToString(entity.copy(distanceKm = null).toDto())
        assertFalse(body, body.contains("version"))
        assertFalse(body, body.contains("null"))
    }

    @Test
    fun `une reponse du serveur avec des champs en plus est lue`() {
        val response = """{"session":{"id":"a","type":"velo","date":"2026-10-08T14:15:00.000Z",""" +
            """"durationMin":95,"distanceKm":38,"note":"Corte","location":null,"version":4,""" +
            """"createdAt":"2026-10-08T14:20:00.000Z","updatedAt":"2026-10-08T14:20:00.000Z"}}"""
        val dto = json.decodeFromString<SessionEnvelope>(response).session
        assertEquals(4, dto.version)
        val stored = dto.toEntity()
        assertEquals(entity.dateMillis, stored.dateMillis)
        assertEquals(SYNC_SYNCED, stored.syncState)
        assertEquals(4, stored.serverVersion)
    }

    @Test
    fun `une liste vide est lue`() {
        assertEquals(0, json.decodeFromString<SessionList>("""{"sessions":[]}""").sessions.size)
    }
}
