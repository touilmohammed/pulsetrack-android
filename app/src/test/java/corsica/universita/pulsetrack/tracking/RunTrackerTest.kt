package corsica.universita.pulsetrack.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La règle de calcul de la course, sans téléphone. android.location.Location n'existe pas sur la JVM :
 * ces tests portent sur la durée et l'arrondi ; la distance est vérifiée sur l'émulateur (parcours simulé).
 */
class RunTrackerTest {

    @Before
    fun reset() = RunTracker.reset()

    @Test
    fun `une course demarree puis arretee donne une duree arrondie a la minute superieure`() {
        RunTracker.start(now = 0)
        RunTracker.stop(now = 61_000)
        val run = RunTracker.state.value.finished!!
        assertEquals(2, run.durationMin)
        assertEquals(0.0, run.distanceKm, 0.0)
        assertFalse(RunTracker.state.value.running)
    }

    @Test
    fun `une course de quelques secondes compte une minute`() {
        RunTracker.start(now = 0)
        RunTracker.stop(now = 5_000)
        assertEquals(1, RunTracker.state.value.finished!!.durationMin)
    }

    @Test
    fun `arreter sans avoir demarre ne produit rien`() {
        RunTracker.stop(now = 10_000)
        assertEquals(null, RunTracker.state.value.finished)
    }

    @Test
    fun `demarrer repart de zero`() {
        RunTracker.start(now = 0)
        RunTracker.stop(now = 120_000)
        RunTracker.start(now = 200_000)
        assertTrue(RunTracker.state.value.running)
        assertEquals(null, RunTracker.state.value.finished)
        assertEquals(200_000, RunTracker.state.value.startedAtMillis)
    }
}
