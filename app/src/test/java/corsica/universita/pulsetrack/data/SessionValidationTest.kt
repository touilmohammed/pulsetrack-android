package corsica.universita.pulsetrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionValidationTest {

    private fun input(duration: String = "45", distance: String = "", note: String = "") =
        SessionInput(SessionType.COURSE, dateMillis = 1_000L, durationMin = duration, distanceKm = distance, note = note)

    private fun valid(result: ValidationResult) = (result as ValidationResult.Valid).value

    @Test
    fun `une seance complete est acceptee`() {
        val value = valid(validateSession(input(distance = "8.2", note = "  Restonica  ")))
        assertEquals(45, value.durationMin)
        assertEquals(8.2, value.distanceKm!!, 0.0)
        assertEquals("Restonica", value.note)
    }

    @Test
    fun `la distance est facultative`() {
        assertEquals(null, valid(validateSession(input(distance = ""))).distanceKm)
    }

    @Test
    fun `la virgule vaut un point et la distance est arrondie a deux decimales`() {
        assertEquals(8.26, valid(validateSession(input(distance = "8,256"))).distanceKm!!, 0.0)
    }

    @Test
    fun `la duree doit etre un entier positif et raisonnable`() {
        listOf("", "0", "-5", "12.5", "abc", "1441").forEach { duration ->
            assertTrue("durée « $duration »", validateSession(input(duration = duration)) is ValidationResult.Invalid)
        }
        assertEquals(1440, valid(validateSession(input(duration = "1440"))).durationMin)
    }

    @Test
    fun `la distance doit etre positive et raisonnable`() {
        listOf("-1", "1000.5", "abc").forEach { distance ->
            assertTrue("distance « $distance »", validateSession(input(distance = distance)) is ValidationResult.Invalid)
        }
    }

    @Test
    fun `la note est limitee a 500 caracteres apres retrait des espaces`() {
        assertTrue(validateSession(input(note = "x".repeat(501))) is ValidationResult.Invalid)
        assertTrue(validateSession(input(note = "x".repeat(500) + "   ")) is ValidationResult.Valid)
    }

    @Test
    fun `toutes les erreurs sont listees ensemble`() {
        val result = validateSession(input(duration = "0", distance = "-1", note = "x".repeat(501)))
        assertEquals(3, (result as ValidationResult.Invalid).errors.size)
    }
}
