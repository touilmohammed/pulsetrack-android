package corsica.universita.pulsetrack

import android.app.Application
import corsica.universita.pulsetrack.data.SessionsRepository

/** Construit une seule fois ce que partagent les écrans, et plus tard les workers et les services. */
object AppContainer {
    lateinit var repository: SessionsRepository
        private set

    fun init() {
        repository = SessionsRepository()
    }
}

/** Créée une fois par process, avant tout écran. */
class PulseTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init()
    }
}
