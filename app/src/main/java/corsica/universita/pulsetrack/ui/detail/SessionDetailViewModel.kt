package corsica.universita.pulsetrack.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.pulsetrack.AppContainer
import corsica.universita.pulsetrack.data.Session
import corsica.universita.pulsetrack.data.SessionsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionDetailViewModel(
    private val repository: SessionsRepository,
    private val sessionId: String,
) : ViewModel() {

    val session: StateFlow<Session?> = repository.session(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(sessionId)
            onDeleted()
        }
    }

    companion object {
        fun factory(sessionId: String) = viewModelFactory {
            initializer { SessionDetailViewModel(AppContainer.repository, sessionId) }
        }
    }
}
