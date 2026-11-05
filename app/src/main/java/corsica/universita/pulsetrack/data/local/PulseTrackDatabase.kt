package corsica.universita.pulsetrack.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class], version = 1, exportSchema = true)
abstract class PulseTrackDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        // Jamais de fallbackToDestructiveMigration : l'utilisateur perdrait ses séances sans message.
        fun build(context: Context): PulseTrackDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PulseTrackDatabase::class.java,
                "pulsetrack.db",
            ).build()
    }
}
