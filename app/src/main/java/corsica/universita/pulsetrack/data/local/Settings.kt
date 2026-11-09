package corsica.universita.pulsetrack.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import corsica.universita.pulsetrack.data.ReminderSettings
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Les petites valeurs isolées : pas des tables, donc DataStore plutôt que Room. */
class Settings(context: Context) {

    private val store = context.applicationContext.settingsStore

    /**
     * L'identité anonyme de cet appareil, envoyée en X-Device-Id comme dans la PWA.
     * edit() est atomique : deux appels simultanés obtiennent le même identifiant.
     */
    suspend fun deviceId(): String {
        var id = ""
        store.edit { prefs -> id = prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[KEY_DEVICE_ID] = it } }
        return id
    }

    suspend fun examplesImported(): Boolean = store.data.first()[KEY_EXAMPLES_IMPORTED] == true

    suspend fun setExamplesImported() {
        store.edit { it[KEY_EXAMPLES_IMPORTED] = true }
    }

    // --- Rappels ---

    val reminders: Flow<ReminderSettings> = store.data.map { prefs ->
        val defaults = ReminderSettings()
        ReminderSettings(
            enabled = prefs[KEY_REMINDERS_ENABLED] ?: defaults.enabled,
            days = prefs[KEY_REMINDER_DAYS]?.split(',')?.mapNotNull(String::toIntOrNull)?.toSet() ?: defaults.days,
            hour = prefs[KEY_REMINDER_HOUR] ?: defaults.hour,
        )
    }

    suspend fun remindersNow(): ReminderSettings = reminders.first()

    suspend fun saveReminders(value: ReminderSettings) {
        store.edit {
            it[KEY_REMINDERS_ENABLED] = value.enabled
            it[KEY_REMINDER_DAYS] = value.days.sorted().joinToString(",")
            it[KEY_REMINDER_HOUR] = value.hour
        }
    }

    // --- Mesure d'audience : null tant que l'utilisateur n'a pas répondu ---

    val analyticsConsent: Flow<Boolean?> = store.data.map { it[KEY_ANALYTICS_CONSENT] }

    suspend fun saveAnalyticsConsent(granted: Boolean) {
        store.edit { it[KEY_ANALYTICS_CONSENT] = granted }
    }

    /** Le jeton FCM : l'adresse de cet appareil pour cette app, que le serveur utilise pour écrire. */
    suspend fun fcmToken(): String? = store.data.first()[KEY_FCM_TOKEN]

    suspend fun saveFcmToken(token: String) {
        store.edit { it[KEY_FCM_TOKEN] = token }
    }

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("deviceId")
        val KEY_EXAMPLES_IMPORTED = booleanPreferencesKey("examplesImported")
        val KEY_REMINDERS_ENABLED = booleanPreferencesKey("remindersEnabled")
        val KEY_REMINDER_DAYS = stringPreferencesKey("reminderDays")
        val KEY_REMINDER_HOUR = intPreferencesKey("reminderHour")
        val KEY_FCM_TOKEN = stringPreferencesKey("fcmToken")
        val KEY_ANALYTICS_CONSENT = booleanPreferencesKey("analyticsConsent")
    }
}
