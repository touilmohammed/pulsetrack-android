package corsica.universita.pulsetrack.ui.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.pulsetrack.AppContainer
import corsica.universita.pulsetrack.data.SessionInput
import corsica.universita.pulsetrack.data.SessionType
import corsica.universita.pulsetrack.data.SessionsRepository
import corsica.universita.pulsetrack.data.ValidationResult
import corsica.universita.pulsetrack.data.validateSession
import corsica.universita.pulsetrack.ui.formatDistance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Le brouillon vit dans SavedStateHandle : il survit à la rotation et à la mort du process.
 * Les erreurs, elles, se recalculent : elles restent en mémoire.
 */
class SessionFormViewModel(
    private val savedState: SavedStateHandle,
    private val repository: SessionsRepository,
    private val sessionId: String?,
) : ViewModel() {

    val type = savedState.getStateFlow(KEY_TYPE, SessionType.COURSE)
    val dateMillis = savedState.getStateFlow(KEY_DATE, System.currentTimeMillis())
    val duration = savedState.getStateFlow(KEY_DURATION, "")
    val distance = savedState.getStateFlow(KEY_DISTANCE, "")
    val note = savedState.getStateFlow(KEY_NOTE, "")

    private val _errors = MutableStateFlow<List<String>>(emptyList())
    val errors: StateFlow<List<String>> = _errors.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val isEditing = sessionId != null

    init {
        // Une seule fois : après une mort du process, le brouillon en cours prime sur la séance.
        if (sessionId != null && savedState.get<Boolean>(KEY_LOADED) != true) {
            viewModelScope.launch {
                repository.get(sessionId)?.let { session ->
                    savedState[KEY_TYPE] = session.type
                    savedState[KEY_DATE] = session.dateMillis
                    savedState[KEY_DURATION] = session.durationMin.toString()
                    savedState[KEY_DISTANCE] = session.distanceKm?.let(::formatDistance).orEmpty()
                    savedState[KEY_NOTE] = session.note
                }
                savedState[KEY_LOADED] = true
            }
        }
    }

    fun setType(value: SessionType) { savedState[KEY_TYPE] = value }
    fun setDate(millis: Long) { savedState[KEY_DATE] = millis }
    fun setDuration(value: String) { savedState[KEY_DURATION] = value }
    fun setDistance(value: String) { savedState[KEY_DISTANCE] = value }
    fun setNote(value: String) { savedState[KEY_NOTE] = value }

    fun save() {
        val input = SessionInput(type.value, dateMillis.value, duration.value, distance.value, note.value)
        when (val result = validateSession(input)) {
            is ValidationResult.Invalid -> _errors.value = result.errors
            is ValidationResult.Valid -> viewModelScope.launch {
                _errors.value = emptyList()
                if (sessionId == null) repository.add(result.value) else repository.update(sessionId, result.value)
                _saved.value = true
            }
        }
    }

    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_DATE = "date"
        private const val KEY_DURATION = "duration"
        private const val KEY_DISTANCE = "distance"
        private const val KEY_NOTE = "note"
        private const val KEY_LOADED = "loaded"

        fun factory(sessionId: String?) = viewModelFactory {
            initializer { SessionFormViewModel(createSavedStateHandle(), AppContainer.repository, sessionId) }
        }
    }
}
