package corsica.universita.pulsetrack

import android.app.Application
import android.content.Context
import corsica.universita.pulsetrack.data.SessionsRepository
import corsica.universita.pulsetrack.data.local.PulseTrackDatabase
import corsica.universita.pulsetrack.data.local.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Construit une seule fois ce que partagent les écrans, et plus tard les workers et les services. */
object AppContainer {
    /** Vit autant que le process : pour le travail qui ne dépend d'aucun écran. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: PulseTrackDatabase
        private set
    lateinit var settings: Settings
        private set
    lateinit var repository: SessionsRepository
        private set

    fun init(context: Context) {
        database = PulseTrackDatabase.build(context)
        settings = Settings(context)
        repository = SessionsRepository(database.sessionDao(), settings)
    }
}

/** Créée une fois par process, avant tout écran. */
class PulseTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        AppContainer.applicationScope.launch { AppContainer.repository.importExamplesOnce() }
    }
}
