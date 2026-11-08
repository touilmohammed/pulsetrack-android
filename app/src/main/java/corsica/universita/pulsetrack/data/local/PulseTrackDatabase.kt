package corsica.universita.pulsetrack.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SessionEntity::class, OutboxEntity::class], version = 2, exportSchema = true)
abstract class PulseTrackDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        /**
         * v1 → v2 : l'état d'envoi, la version du serveur et l'outbox.
         * Les séances créées en v1 n'ont jamais été envoyées : on les met en file au passage,
         * sinon la mise à jour les laisserait pour toujours sur le seul téléphone.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN syncState TEXT NOT NULL DEFAULT 'synced'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN serverVersion INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS outbox (seq INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "opId TEXT NOT NULL, sessionId TEXT NOT NULL, kind TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                )
                // Un exemple jamais modifié n'est pas une séance de l'utilisateur : il ne part pas.
                db.execSQL("UPDATE sessions SET syncState = 'pending' WHERE isExample = 0")
                // opId au format UUID (8-4-4-4-12), exigé par le serveur pour Idempotency-Key.
                db.execSQL(
                    "INSERT INTO outbox (opId, sessionId, kind, createdAt) " +
                        "SELECT lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(6))), " +
                        "id, 'upsert', CAST(strftime('%s','now') AS INTEGER) * 1000 " +
                        "FROM sessions WHERE isExample = 0 ORDER BY dateMillis",
                )
            }
        }

        // Jamais de fallbackToDestructiveMigration : l'utilisateur perdrait ses séances sans message.
        fun build(context: Context): PulseTrackDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PulseTrackDatabase::class.java,
                "pulsetrack.db",
            ).addMigrations(MIGRATION_1_2).build()
    }
}
