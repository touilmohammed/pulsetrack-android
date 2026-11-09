package corsica.universita.pulsetrack.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import corsica.universita.pulsetrack.data.local.Settings
import kotlinx.coroutines.flow.Flow

/**
 * La mesure d'audience : trois événements (activation, rétention, fonctionnalité native) et les écrans vus.
 * Rien ne part avant le « oui » : la collecte est coupée dans le manifest, puis ouverte ou non selon le choix.
 * Aucune donnée personnelle dans un événement : ni texte de séance, ni position, ni identifiant.
 * Comme l'outbox : le SDK écrit sur le disque et envoie par lots, hors ligne compris.
 */
class Analytics(context: Context, private val settings: Settings) {

    private val app = context.applicationContext

    /** Sans google-services.json, Firebase n'est pas initialisé : l'analytics ne fait rien. */
    val available: Boolean = FirebaseApp.getApps(app).isNotEmpty()

    private val firebase: FirebaseAnalytics? = if (available) FirebaseAnalytics.getInstance(app) else null

    /** null : jamais demandé ; true / false : la réponse de l'utilisateur. */
    val consent: Flow<Boolean?> = settings.analyticsConsent

    suspend fun setConsent(granted: Boolean) {
        settings.saveAnalyticsConsent(granted)
        // Mémorisé aussi par le SDK : il reste valable au prochain lancement.
        firebase?.setAnalyticsCollectionEnabled(granted)
    }

    // --- Le plan de marquage : un nom par question, en snake_case, jamais renommé ---

    /** Activation : le premier geste utile. offline : la séance est-elle écrite sans réseau ? */
    fun sessionCreated(fromRun: Boolean, offline: Boolean) = log(
        "session_created",
        "source" to if (fromRun) "run" else "manual",
        "offline" to if (offline) 1L else 0L,
    )

    /** Rétention : un rappel touché fait-il revenir ? (nos push sont des messages data : à poser soi-même) */
    fun reminderOpened() = log("reminder_opened")

    /** Le natif : le suivi sert-il, réussit-il, ou bute-t-il sur la permission ? */
    fun runTracked(result: String, distanceKm: Double = 0.0, durationMin: Int = 0) = log(
        "run_tracked",
        "result" to result,
        "distance_km" to distanceKm,
        "duration_min" to durationMin.toLong(),
    )

    /** Avec Compose, une seule Activity : sans ceci, chaque écran s'appellerait MainActivity. */
    fun screen(name: String) = log(
        FirebaseAnalytics.Event.SCREEN_VIEW,
        FirebaseAnalytics.Param.SCREEN_NAME to name,
    )

    private fun log(name: String, vararg params: Pair<String, Any>) {
        val analytics = firebase ?: return
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
            }
        }
        analytics.logEvent(name, bundle)
    }
}
