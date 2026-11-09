package corsica.universita.pulsetrack

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import corsica.universita.pulsetrack.data.LocationSource
import corsica.universita.pulsetrack.data.NetworkMonitor
import corsica.universita.pulsetrack.data.PhotoStore
import corsica.universita.pulsetrack.data.RemindersRepository
import corsica.universita.pulsetrack.data.SessionsRepository
import corsica.universita.pulsetrack.data.local.PulseTrackDatabase
import corsica.universita.pulsetrack.data.local.Settings
import corsica.universita.pulsetrack.data.remote.PushApi
import corsica.universita.pulsetrack.data.remote.SessionApi
import corsica.universita.pulsetrack.data.remote.okHttp
import corsica.universita.pulsetrack.data.remote.retrofit
import corsica.universita.pulsetrack.notif.Notifications
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
    lateinit var photos: PhotoStore
        private set
    lateinit var location: LocationSource
        private set
    lateinit var reminders: RemindersRepository
        private set

    fun init(context: Context) {
        database = PulseTrackDatabase.build(context)
        settings = Settings(context)
        network = NetworkMonitor(context)
        photos = PhotoStore(context)
        location = LocationSource(context)
        val retrofit = retrofit(okHttp(context))
        val workManager = WorkManager.getInstance(context)
        repository = SessionsRepository(
            database = database,
            settings = settings,
            api = retrofit.create(SessionApi::class.java),
            workManager = workManager,
            photos = photos,
        )
        reminders = RemindersRepository(context, settings, retrofit.create(PushApi::class.java), workManager)
    }
}

/** Créée une fois par process, avant tout écran, et aussi quand l'OS relance l'app pour un worker. */
class PulseTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        // Avant toute notification, même celle d'un push qui réveille l'app tuée.
        Notifications.createChannels(this)
        AppContainer.applicationScope.launch {
            val repository = AppContainer.repository
            repository.importExamplesOnce()
            repository.enqueueSyncIfPending()
            // Rafraîchir est un plus : hors ligne, l'écran affiche déjà Room.
            runCatching { repository.refresh() }.onFailure { Log.i("PulseTrackApp", "Pas de rafraîchissement : ${it.message}") }
        }
    }
}
