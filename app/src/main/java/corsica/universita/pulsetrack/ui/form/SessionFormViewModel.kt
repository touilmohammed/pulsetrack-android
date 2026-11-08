package corsica.universita.pulsetrack.ui.form

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.pulsetrack.AppContainer
import corsica.universita.pulsetrack.data.LocationSource
import corsica.universita.pulsetrack.data.PhotoStore
import corsica.universita.pulsetrack.data.SessionInput
import corsica.universita.pulsetrack.data.SessionLocation
import corsica.universita.pulsetrack.data.SessionType
import corsica.universita.pulsetrack.data.SessionsRepository
import corsica.universita.pulsetrack.data.ValidationResult
import corsica.universita.pulsetrack.data.validateSession
import corsica.universita.pulsetrack.ui.formatDistance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Le brouillon vit dans SavedStateHandle : il survit à la rotation et à la mort du process.
 * Les erreurs et les messages, eux, se recalculent : ils restent en mémoire.
 */
class SessionFormViewModel(
    private val savedState: SavedStateHandle,
    private val repository: SessionsRepository,
    private val photos: PhotoStore,
    private val locationSource: LocationSource,
    private val sessionId: String?,
) : ViewModel() {

    val type = savedState.getStateFlow(KEY_TYPE, SessionType.COURSE)
    val dateMillis = savedState.getStateFlow(KEY_DATE, System.currentTimeMillis())
    val duration = savedState.getStateFlow(KEY_DURATION, "")
    val distance = savedState.getStateFlow(KEY_DISTANCE, "")
    val note = savedState.getStateFlow(KEY_NOTE, "")
    val photoPath = savedState.getStateFlow<String?>(KEY_PHOTO, null)

    val location: StateFlow<SessionLocation?> = combine(
        savedState.getStateFlow<Double?>(KEY_LAT, null),
        savedState.getStateFlow<Double?>(KEY_LNG, null),
        savedState.getStateFlow<Int?>(KEY_ACCURACY, null),
    ) { lat, lng, accuracy -> if (lat != null && lng != null) SessionLocation(lat, lng, accuracy) else null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _errors = MutableStateFlow<List<String>>(emptyList())
    val errors: StateFlow<List<String>> = _errors.asStateFlow()

    /** Message sous « Lieu et photo » : position introuvable, image illisible… Jamais une exception. */
    private val _captureMessage = MutableStateFlow<String?>(null)
    val captureMessage: StateFlow<String?> = _captureMessage.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

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
                    setLocation(session.location)
                    savedState[KEY_PHOTO] = session.photoPath
                    savedState[KEY_ORIGINAL_PHOTO] = session.photoPath
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

    // --- Position : appelée seulement quand la permission est accordée (voir PermissionFlow) ---

    fun locate() {
        if (_locating.value) return
        viewModelScope.launch {
            _locating.value = true
            _captureMessage.value = null
            val found = locationSource.current()
            _locating.value = false
            if (found == null) _captureMessage.value = MESSAGE_NO_LOCATION else setLocation(found)
        }
    }

    fun removeLocation() = setLocation(null)

    private fun setLocation(value: SessionLocation?) {
        savedState[KEY_LAT] = value?.lat
        savedState[KEY_LNG] = value?.lng
        savedState[KEY_ACCURACY] = value?.accuracy
    }

    // --- Photo ---

    /**
     * L'adresse où l'appareil photo écrira. Gardée dans SavedStateHandle : pendant qu'on cadre,
     * l'OS peut tuer PulseTrack pour libérer la mémoire, et il faut la retrouver au retour.
     */
    fun newCaptureUri(): Uri = photos.newCaptureUri().also { savedState[KEY_CAPTURE] = it.toString() }

    fun onPhotoTaken(success: Boolean) {
        Log.d(TAG, "Appareil photo : ${if (success) "photo prise" else "annulé"}")
        val uri = savedState.get<String>(KEY_CAPTURE)?.let(Uri::parse) ?: return
        savedState[KEY_CAPTURE] = null
        if (success) importPhoto(uri) else viewModelScope.launch { photos.discardCapture(uri) }
    }

    fun onPhotoPicked(uri: Uri?) {
        Log.d(TAG, "Image choisie : ${uri ?: "aucune (sélection annulée)"}")
        if (uri != null) importPhoto(uri)
    }

    fun removePhoto() {
        val current = photoPath.value
        savedState[KEY_PHOTO] = null
        discardIfDraft(current)
    }

    private fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            _captureMessage.value = null
            val path = photos.import(uri)
            Log.d(TAG, "Import : ${path ?: "échec"}")
            if (path == null) {
                _captureMessage.value = MESSAGE_BAD_IMAGE
                return@launch
            }
            val previous = photoPath.value
            savedState[KEY_PHOTO] = path
            discardIfDraft(previous)
        }
    }

    /** Une photo prise pour ce brouillon et remplacée avant d'enregistrer : personne d'autre ne s'en sert. */
    private fun discardIfDraft(path: String?) {
        if (path != null && path != savedState.get<String>(KEY_ORIGINAL_PHOTO)) {
            AppContainer.applicationScope.launch { photos.delete(path) }
        }
    }

    fun save() {
        val input = SessionInput(
            type = type.value,
            dateMillis = dateMillis.value,
            durationMin = duration.value,
            distanceKm = distance.value,
            note = note.value,
            location = location.value,
            photoPath = photoPath.value,
        )
        when (val result = validateSession(input)) {
            is ValidationResult.Invalid -> _errors.value = result.errors
            is ValidationResult.Valid -> viewModelScope.launch {
                _errors.value = emptyList()
                if (sessionId == null) repository.add(result.value) else repository.update(sessionId, result.value)
                _saved.value = true
            }
        }
    }

    /** Formulaire abandonné pour de bon (pas une rotation) : la photo du brouillon ne servira plus. */
    override fun onCleared() {
        if (!_saved.value) discardIfDraft(photoPath.value)
    }

    companion object {
        private const val TAG = "SessionForm"
        private const val KEY_TYPE = "type"
        private const val KEY_DATE = "date"
        private const val KEY_DURATION = "duration"
        private const val KEY_DISTANCE = "distance"
        private const val KEY_NOTE = "note"
        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"
        private const val KEY_ACCURACY = "accuracy"
        private const val KEY_PHOTO = "photo"
        private const val KEY_ORIGINAL_PHOTO = "originalPhoto"
        private const val KEY_CAPTURE = "captureUri"
        private const val KEY_LOADED = "loaded"

        const val MESSAGE_NO_LOCATION = "Position indisponible pour l'instant. Réessayez à l'extérieur, ou enregistrez sans."
        const val MESSAGE_BAD_IMAGE = "Image illisible : choisissez-en une autre."

        fun factory(sessionId: String?) = viewModelFactory {
            initializer {
                SessionFormViewModel(
                    createSavedStateHandle(),
                    AppContainer.repository,
                    AppContainer.photos,
                    AppContainer.location,
                    sessionId,
                )
            }
        }
    }
}
