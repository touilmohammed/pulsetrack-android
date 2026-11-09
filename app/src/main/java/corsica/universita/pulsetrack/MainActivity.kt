package corsica.universita.pulsetrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import corsica.universita.pulsetrack.links.AppLinks
import corsica.universita.pulsetrack.links.LinkTarget
import corsica.universita.pulsetrack.ui.SessionLinkRequest
import androidx.compose.runtime.setValue
import corsica.universita.pulsetrack.notif.Notifications
import corsica.universita.pulsetrack.ui.PulseTrackNavigation
import corsica.universita.pulsetrack.ui.theme.PulseTrackTheme

class MainActivity : ComponentActivity() {

    /** Nombre de clics sur un rappel à traiter : un compteur plutôt qu'un booléen, chaque clic compte. */
    private var newSessionRequests by mutableIntStateOf(0)
    private var trackingRequests by mutableIntStateOf(0)
    /** Le dernier lien de séance reçu ; un nouvel objet à chaque lien, même s'il vise la même séance. */
    private var sessionLink by mutableStateOf<SessionLinkRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // savedInstanceState non nul : l'Activity est recréée (rotation), le clic a déjà été traité.
        if (savedInstanceState == null) handle(intent)
        setContent {
            PulseTrackTheme {
                PulseTrackNavigation(newSessionRequests, trackingRequests, sessionLink)
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
            Notifications.ACTION_NEW_SESSION -> {
                newSessionRequests++
                AppContainer.analytics.reminderOpened()
            }
            Notifications.ACTION_OPEN_TRACKING -> trackingRequests++
            Intent.ACTION_VIEW -> when (val target = AppLinks.parse(intent.dataString)) {
                LinkTarget.NewSession -> newSessionRequests++
                is LinkTarget.SessionDetail -> sessionLink = SessionLinkRequest(target.id)
                LinkTarget.Journal, null -> Unit // l'app s'ouvre sur le journal ; un lien invalide ne fait rien de plus
            }
        }
    }
}
