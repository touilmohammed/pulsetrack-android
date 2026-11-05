package corsica.universita.pulsetrack.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import corsica.universita.pulsetrack.ui.detail.SessionDetailScreen
import corsica.universita.pulsetrack.ui.form.SessionFormScreen
import corsica.universita.pulsetrack.ui.journal.JournalScreen
import kotlinx.serialization.Serializable

// Chaque écran est une clé sérialisable : la pile survit à la rotation et à la mort du process.
@Serializable
data object Journal : NavKey

@Serializable
data class SessionDetail(val id: String) : NavKey

/** [id] nul : nouvelle séance. */
@Serializable
data class SessionForm(val id: String? = null) : NavKey

@Composable
fun PulseTrackNavigation() {
    val backStack = rememberNavBackStack(Journal)

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
                )
            }
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
                    onDone = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
