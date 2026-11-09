package corsica.universita.pulsetrack.notif

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import corsica.universita.pulsetrack.AppContainer
import kotlinx.coroutines.launch

/**
 * Jamais lancé par l'app : c'est l'OS (Play Services) qui le réveille quand un push arrive,
 * même si PulseTrack a été tuée. Application.onCreate s'exécute d'abord : les canaux existent déjà.
 */
class PulseTrackMessagingService : FirebaseMessagingService() {

    /** Le jeton est l'adresse de cette app sur ce téléphone ; il peut changer, le serveur doit le suivre. */
    override fun onNewToken(token: String) {
        Log.i(TAG, "Nouveau jeton FCM")
        AppContainer.applicationScope.launch { AppContainer.reminders.onNewToken(token) }
    }

    /**
     * Le serveur envoie des messages « data » : cette fonction est appelée dans tous les cas,
     * et c'est l'app qui construit la notification, sur son canal et avec son clic.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Log.i(TAG, "Push reçu : ${data["body"]}")
        Notifications.showReminder(
            context = this,
            title = data["title"] ?: message.notification?.title ?: "PulseTrack",
            body = data["body"] ?: message.notification?.body.orEmpty(),
        )
    }

    private companion object {
        const val TAG = "PulseTrackFcm"
    }
}
