package corsica.universita.pulsetrack.ui.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.pulsetrack.AppContainer
import corsica.universita.pulsetrack.data.Session
import corsica.universita.pulsetrack.data.SessionType
import corsica.universita.pulsetrack.data.SessionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class JournalState(
    val filter: SessionType? = null,
    val sessions: List<Session> = emptyList(),
    val pendingCount: Int = 0,
    val online: Boolean = true,
)

class JournalViewModel(
    private val savedState: SavedStateHandle,
    private val repository: SessionsRepository,
    online: Flow<Boolean>,
) : ViewModel() {

    private val filter = savedState.getStateFlow<SessionType?>(KEY_FILTER, null)

    val state: StateFlow<JournalState> =
        combine(repository.sessions, filter, repository.pendingCount, online) { sessions, type, pending, isOnline ->
            JournalState(
                filter = type,
                sessions = if (type == null) sessions else sessions.filter { it.type == type },
                pendingCount = pending,
                online = isOnline,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalState())

    fun setFilter(type: SessionType?) {
        savedState[KEY_FILTER] = type
    }

    /** « Synchroniser maintenant » : une demande à l'OS, qui attend toujours le réseau. */
    fun syncNow() = repository.enqueueSync()

    companion object {
        private const val KEY_FILTER = "filter"

        val Factory = viewModelFactory {
            initializer {
                JournalViewModel(createSavedStateHandle(), AppContainer.repository, AppContainer.network.isOnline)
            }
        }
    }
}
