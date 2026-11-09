package corsica.universita.pulsetrack.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import corsica.universita.pulsetrack.data.local.Settings
import corsica.universita.pulsetrack.data.remote.ApiError
import corsica.universita.pulsetrack.data.remote.FcmRegistration
import corsica.universita.pulsetrack.data.remote.FcmToken
import corsica.universita.pulsetrack.data.remote.PushApi
import corsica.universita.pulsetrack.data.remote.ReminderDto
import corsica.universita.pulsetrack.data.remote.json
import corsica.universita.pulsetrack.work.PushRegistrationWorker
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Response

/** Le choix de l'utilisateur. days : 0 = dimanche … 6 = samedi. */
data class ReminderSettings(val enabled: Boolean = false, val days: Set<Int> = setOf(1, 3, 5), val hour: Int = 18)

/** Ce que l'écran doit dire après une action : jamais une exception. */
sealed interface ReminderOutcome {
    data object Ok : ReminderOutcome
    data object Offline : ReminderOutcome
    data class Failed(val message: String) : ReminderOutcome
}

/**
 * Les rappels d'entraînement : le choix (jours, heure) vit dans DataStore, l'abonnement sur le serveur,
 * qui envoie le push à l'heure dite. Mêmes jours par défaut que la PWA : lundi, mercredi, vendredi à 18 h.
 */
class RemindersRepository(
    context: Context,
    private val settings: Settings,
    private val api: PushApi,
    private val workManager: WorkManager,
) {
    private val app = context.applicationContext

    val reminders: Flow<ReminderSettings> = settings.reminders

    /** Sans google-services.json, Firebase n'est pas initialisé : pas de jeton, donc pas de rappels. */
    fun available(): Boolean = FirebaseApp.getApps(app).isNotEmpty()

    suspend fun enable(days: Set<Int>, hour: Int): ReminderOutcome = call {
        val token = token() ?: return@call ReminderOutcome.Failed("Jeton de notification indisponible : vérifiez les Play Services.")
        val response = api.register(settings.deviceId(), registration(token, days, hour))
        if (!response.isSuccessful) return@call ReminderOutcome.Failed(errorOf(response))
        settings.saveReminders(ReminderSettings(enabled = true, days = days, hour = hour))
        settings.saveFcmToken(token)
        ReminderOutcome.Ok
    }

    suspend fun disable(): ReminderOutcome = call {
        val token = settings.fcmToken() ?: token()
        if (token != null) {
            val response = api.unregister(settings.deviceId(), FcmToken(token))
            if (!response.isSuccessful) return@call ReminderOutcome.Failed(errorOf(response))
        }
        settings.saveReminders(ReminderSettings(enabled = false, days = settings.remindersNow().days, hour = settings.remindersNow().hour))
        ReminderOutcome.Ok
    }

    suspend fun sendTest(): ReminderOutcome = call {
        val response = api.test(settings.deviceId())
        when {
            !response.isSuccessful -> ReminderOutcome.Failed(errorOf(response))
            (response.body()?.sent ?: 0) == 0 -> ReminderOutcome.Failed("Le serveur n'a pas pu joindre ce téléphone. Réactivez les rappels.")
            else -> ReminderOutcome.Ok
        }
    }

    /**
     * Firebase change parfois le jeton (réinstallation, données effacées) : sans nouvel abonnement,
     * le serveur écrirait à une adresse morte. Confié à WorkManager : part dès qu'il y a du réseau.
     */
    suspend fun onNewToken(token: String) {
        settings.saveFcmToken(token)
        if (settings.remindersNow().enabled) {
            val request = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(PushRegistrationWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    /** Appelée par PushRegistrationWorker. Une panne réseau lève IOException : le worker réessaie. */
    suspend fun reRegister(): Boolean {
        val current = settings.remindersNow()
        if (!current.enabled) return true
        val token = settings.fcmToken() ?: token() ?: return false
        return api.register(settings.deviceId(), registration(token, current.days, current.hour)).isSuccessful
    }

    private fun registration(token: String, days: Set<Int>, hour: Int) =
        FcmRegistration(token, ReminderDto(days.sorted(), hour, ZoneId.systemDefault().id))

    /** Le callback des Play Services devient une fonction suspend, comme pour la position. */
    private suspend fun token(): String? {
        if (!available()) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        }
    }

    private suspend fun call(block: suspend () -> ReminderOutcome): ReminderOutcome = try {
        block()
    } catch (_: IOException) {
        ReminderOutcome.Offline
    }

    private fun errorOf(response: Response<*>): String {
        val message = response.errorBody()?.string()
            ?.let { runCatching { json.decodeFromString<ApiError>(it).error }.getOrNull() }
        return message?.takeIf { it.isNotBlank() } ?: "Erreur du serveur (${response.code()})."
    }
}
