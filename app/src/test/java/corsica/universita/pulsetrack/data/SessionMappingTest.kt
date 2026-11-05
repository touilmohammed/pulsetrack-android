package corsica.universita.pulsetrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionMappingTest {

    private val session = Session(
        id = "a",
        type = SessionType.VELO,
        dateMillis = 1_000L,
        durationMin = 95,
        distanceKm = 38.0,
        note = "Corte",
        isExample = true,
    )

    @Test
    fun `une seance survit a l'aller-retour vers le disque`() {
        assertEquals(session, session.toEntity().toSession())
    }

    @Test
    fun `le type est stocke avec la valeur de l'API`() {
        assertEquals("velo", session.toEntity().type)
    }

    @Test
    fun `un type inconnu ne fait pas planter`() {
        assertEquals(SessionType.AUTRE, session.toEntity().copy(type = "natation").toSession().type)
    }

    @Test
    fun `une modification enleve le statut d'exemple`() {
        val value = ValidSession(SessionType.VELO, 1_000L, 95, 38.0, "Corte")
        assertFalse(value.toEntity("seed-3").isExample)
    }
}
