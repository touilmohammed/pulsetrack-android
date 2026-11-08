package corsica.universita.pulsetrack

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import corsica.universita.pulsetrack.data.NetworkMonitor
import corsica.universita.pulsetrack.data.SessionsRepository
import corsica.universita.pulsetrack.data.local.PulseTrackDatabase
import corsica.universita.pulsetrack.data.local.Settings
import corsica.universita.pulsetrack.data.remote.okHttp
import corsica.universita.pulsetrack.data.remote.sessionApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Construit une seule fois ce que partagent les écrans, les workers et plus tard les services. */
object AppContainer {
    /** Vit autant que le process : pour le travail qui ne dépend d'aucun écran. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: PulseTrackDatabase
        private set
    lateinit var settings: Settings
        private set
    lateinit var repository: SessionsRepository
        private set
    lateinit var network: NetworkMonitor
        private set

    fun init(context: Context) {
        database = PulseTrackDatabase.build(context)
        settings = Settings(context)
        network = NetworkMonitor(context)
        repository = SessionsRepository(
            database = database,
            settings = settings,
            api = sessionApi(okHttp(context)),
            workManager = WorkManager.getInstance(context),
        )
    }
}

/** Créée une fois par process, avant tout écran, et aussi quand l'OS relance l'app pour un worker. */
class PulseTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        AppContainer.applicationScope.launch {
            val repository = AppContainer.repository
            repository.importExamplesOnce()
            repository.enqueueSyncIfPending()
            // Rafraîchir est un plus : hors ligne, l'écran affiche déjà Room.
            runCatching { repository.refresh() }.onFailure { Log.i("PulseTrackApp", "Pas de rafraîchissement : ${it.message}") }
        }
    }
}
