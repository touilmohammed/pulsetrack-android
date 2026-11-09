package corsica.universita.pulsetrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import corsica.universita.pulsetrack.notif.Notifications
import corsica.universita.pulsetrack.ui.PulseTrackNavigation
import corsica.universita.pulsetrack.ui.theme.PulseTrackTheme

class MainActivity : ComponentActivity() {

    /** Nombre de clics sur un rappel à traiter : un compteur plutôt qu'un booléen, chaque clic compte. */
    private var newSessionRequests by mutableIntStateOf(0)
    private var trackingRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // savedInstanceState non nul : l'Activity est recréée (rotation), le clic a déjà été traité.
        if (savedInstanceState == null) handle(intent)
        setContent {
            PulseTrackTheme {
                PulseTrackNavigation(newSessionRequests, trackingRequests)
            }
        }
    }

    /** L'app était déjà ouverte (singleTop) : le clic sur la notification arrive ici. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            Notifications.ACTION_NEW_SESSION -> newSessionRequests++
            Notifications.ACTION_OPEN_TRACKING -> trackingRequests++
        }
    }
}
