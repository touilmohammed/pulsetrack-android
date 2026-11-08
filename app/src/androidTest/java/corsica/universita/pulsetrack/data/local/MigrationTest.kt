package corsica.universita.pulsetrack.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rejoue sur l'appareil ce que vit un utilisateur qui met à jour : une base v1 remplie,
 * migrée en v2, puis comparée par Room au schéma exporté 2.json.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PulseTrackDatabase::class.java,
    )

    @Test
    fun migration1To2KeepsSessionsAndQueuesUserSessions() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO sessions (id, type, dateMillis, durationMin, distanceKm, note, isExample) " +
                    "VALUES ('a', 'course', 1000, 30, 5.5, 'Footing', 0)",
            )
            execSQL(
                "INSERT INTO sessions (id, type, dateMillis, durationMin, distanceKm, note, isExample) " +
                    "VALUES ('seed-1', 'course', 500, 45, 8.2, 'Restonica', 1)",
            )
            close()
        }

        // validateDroppedTables = true : Room vérifie aussi qu'aucune table en trop ne reste.
        val db = helper.runMigrationsAndValidate(DB, 2, true, PulseTrackDatabase.MIGRATION_1_2)

        db.query("SELECT id, note, syncState, serverVersion FROM sessions ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToNext()
            assertEquals("a", c.getString(0))
            assertEquals("Footing", c.getString(1))
            assertEquals("pending", c.getString(2)) // jamais envoyée : en attente
            assertTrue(c.isNull(3))
            c.moveToNext()
            assertEquals("seed-1", c.getString(0))
            assertEquals("synced", c.getString(2)) // exemple : rien à envoyer
        }

        db.query("SELECT opId, sessionId, kind FROM outbox").use { c ->
            assertEquals(1, c.count) // seule la séance de l'utilisateur part
            c.moveToNext()
            assertTrue("opId au format UUID : ${c.getString(0)}", UUID_RE.matches(c.getString(0)))
            assertEquals("a", c.getString(1))
            assertEquals(OP_UPSERT, c.getString(2))
        }
    }

    private companion object {
        const val DB = "migration-test.db"
        val UUID_RE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
