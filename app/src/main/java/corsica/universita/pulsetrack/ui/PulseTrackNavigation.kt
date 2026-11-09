package corsica.universita.pulsetrack.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import corsica.universita.pulsetrack.R
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import corsica.universita.pulsetrack.ui.detail.SessionDetailScreen
import corsica.universita.pulsetrack.ui.form.SessionFormScreen
import corsica.universita.pulsetrack.ui.journal.JournalScreen
import corsica.universita.pulsetrack.ui.settings.SettingsScreen
import corsica.universita.pulsetrack.ui.tracking.TrackingScreen
import corsica.universita.pulsetrack.AppContainer
import kotlinx.serialization.Serializable

// Chaque écran est une clé sérialisable : la pile survit à la rotation et à la mort du process.
@Serializable
data object Journal : NavKey

@Serializable
data class SessionDetail(val id: String) : NavKey

/** [id] nul : nouvelle séance ; [run] : préremplie par une course suivie au GPS. */
@Serializable
data class SessionForm(val id: String? = null, val run: TrackedRun? = null) : NavKey

/** Ce que le suivi GPS transmet au formulaire : une clé de navigation, donc sérialisable. */
@Serializable
data class TrackedRun(
    val startedAtMillis: Long,
    val durationMin: Int,
    val distanceKm: Double,
    val startLat: Double? = null,
    val startLng: Double? = null,
)

@Serializable
data object Tracking : NavKey

@Serializable
data object Settings : NavKey

/** Un lien de séance à ouvrir. Une classe simple (pas data) : deux liens vers la même séance restent distincts. */
class SessionLinkRequest(val id: String)

/**
 * [newSessionRequests] augmente à chaque clic sur un rappel : on ouvre « Nouvelle séance ».
 * [trackingRequests] augmente à chaque clic sur la notification du suivi : on ouvre le suivi.
 */
@Composable
fun PulseTrackNavigation(newSessionRequests: Int = 0, trackingRequests: Int = 0, sessionLink: SessionLinkRequest? = null) {
    val backStack = rememberNavBackStack(Journal)
    val context = LocalContext.current

    // Un lien de séance : elle peut ne pas être encore sur ce téléphone (créée ailleurs, pas encore récupérée).
    // On tente une synchronisation avant de conclure qu'elle est introuvable, comme le ferait la PWA.
    LaunchedEffect(sessionLink) {
        val id = sessionLink?.id ?: return@LaunchedEffect
        val repository = AppContainer.repository
        val found = repository.get(id) ?: run {
            runCatching { repository.refresh() }
            repository.get(id)
        }
        when {
            found == null -> Toast.makeText(context, R.string.link_session_not_found, Toast.LENGTH_LONG).show()
            backStack.lastOrNull() != SessionDetail(id) -> backStack.add(SessionDetail(id))
        }
    }

    LaunchedEffect(newSessionRequests) {
        if (newSessionRequests > 0 && backStack.lastOrNull() != SessionForm()) backStack.add(SessionForm())
    }
    // L'écran vu, envoyé à la main : le suivi automatique ne verrait qu'une Activity.
    val current = backStack.lastOrNull()
    LaunchedEffect(current) {
        val name = when (current) {
            Journal -> "journal"
            is SessionDetail -> "session_detail"
            is SessionForm -> "session_form"
            Settings -> "settings"
            Tracking -> "tracking"
            else -> return@LaunchedEffect
        }
        AppContainer.analytics.screen(name)
    }

    LaunchedEffect(trackingRequests) {
        if (trackingRequests > 0 && backStack.lastOrNull() != Tracking) backStack.add(Tracking)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Sans le second décorateur, chaque ViewModel vivrait aussi longtemps que l'Activity :
        // un formulaire fermé garderait son brouillon pour la séance suivante.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Journal> {
                JournalScreen(
                    onNewSession = { backStack.add(SessionForm()) },
                    onOpenSession = { id -> backStack.add(SessionDetail(id)) },
                    onOpenSettings = { backStack.add(Settings) },
                    onOpenTracking = { backStack.add(Tracking) },
                )
            }
            entry<Tracking> {
                TrackingScreen(onSave = { run ->
                    // Le suivi quitte la pile : après l'enregistrement, retour au journal.
                    backStack.removeLastOrNull()
                    backStack.add(SessionForm(run = TrackedRun(run.startedAtMillis, run.durationMin, run.distanceKm, run.startLat, run.startLng)))
                })
            }
            entry<Settings> { SettingsScreen() }
            entry<SessionDetail> { key ->
                SessionDetailScreen(
                    sessionId = key.id,
                    onEdit = { backStack.add(SessionForm(key.id)) },
                    onClose = { backStack.removeLastOrNull() },
                )
            }
            entry<SessionForm> { key ->
                SessionFormScreen(
                    sessionId = key.id,
                    run = key.run,
                    onDone = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
