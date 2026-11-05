package corsica.universita.pulsetrack.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.first

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

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("deviceId")
        val KEY_EXAMPLES_IMPORTED = booleanPreferencesKey("examplesImported")
    }
}
