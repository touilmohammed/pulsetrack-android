package corsica.universita.pulsetrack.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.pulsetrack.AppContainer
import corsica.universita.pulsetrack.analytics.Analytics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import corsica.universita.pulsetrack.data.ReminderOutcome
import corsica.universita.pulsetrack.data.RemindersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Le texte sous les boutons : ce qui vient de se passer, toujours en mots, jamais une erreur technique. */
sealed interface ReminderStatus {
    data object None : ReminderStatus
    data object Enabled : ReminderStatus
    data object Disabled : ReminderStatus
    data object TestSent : ReminderStatus
    data object Offline : ReminderStatus
    data object NoDays : ReminderStatus
    data class Failed(val message: String) : ReminderStatus
}

class SettingsViewModel(
    private val savedState: SavedStateHandle,
    private val reminders: RemindersRepository,
    private val analytics: Analytics,
) : ViewModel() {

    val analyticsAvailable = analytics.available
    val analyticsConsent: StateFlow<Boolean?> = analytics.consent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAnalyticsConsent(granted: Boolean) {
        viewModelScope.launch { analytics.setConsent(granted) }
    }

    val available = reminders.available()

    /** Le choix en cours (pas encore envoyé) : il survit à la rotation et à la mort du process. */
    val days = savedState.getStateFlow(KEY_DAYS, intArrayOf())
    val hour = savedState.getStateFlow(KEY_HOUR, -1)

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _status = MutableStateFlow<ReminderStatus>(ReminderStatus.None)
    val status: StateFlow<ReminderStatus> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = reminders.reminders.first()
            _enabled.value = saved.enabled
            // Après une mort du process, le choix en cours prime sur celui qui est enregistré.
            if (hour.value < 0) {
                savedState[KEY_DAYS] = saved.days.sorted().toIntArray()
                savedState[KEY_HOUR] = saved.hour
            }
        }
    }

    fun toggleDay(day: Int) {
        val current = days.value.toSet()
        savedState[KEY_DAYS] = (if (day in current) current - day else current + day).sorted().toIntArray()
    }

    fun setHour(value: Int) { savedState[KEY_HOUR] = value }

    /** Appelée une fois la permission de notifier accordée. */
    fun enable() {
        if (days.value.isEmpty()) {
            _status.value = ReminderStatus.NoDays
            return
        }
        perform(ReminderStatus.Enabled) { reminders.enable(days.value.toSet(), hour.value) }
    }

    fun disable() = perform(ReminderStatus.Disabled) { reminders.disable() }

    fun sendTest() = perform(ReminderStatus.TestSent) { reminders.sendTest() }

    private fun perform(success: ReminderStatus, action: suspend () -> ReminderOutcome) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _status.value = when (val outcome = action()) {
                ReminderOutcome.Ok -> success
                ReminderOutcome.Offline -> ReminderStatus.Offline
                is ReminderOutcome.Failed -> ReminderStatus.Failed(outcome.message)
            }
            _enabled.value = reminders.reminders.first().enabled
            _busy.value = false
        }
    }

    companion object {
        private const val KEY_DAYS = "days"
        private const val KEY_HOUR = "hour"

        val Factory = viewModelFactory {
            initializer { SettingsViewModel(createSavedStateHandle(), AppContainer.reminders, AppContainer.analytics) }
        }
    }
}
