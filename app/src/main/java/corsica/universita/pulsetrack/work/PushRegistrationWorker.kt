package corsica.universita.pulsetrack.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import corsica.universita.pulsetrack.AppContainer
import java.io.IOException

/** Réabonne l'app au serveur avec son nouveau jeton FCM, dès qu'il y a du réseau, même app fermée. */
class PushRegistrationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        if (AppContainer.reminders.reRegister()) Result.success() else Result.retry()
    } catch (_: IOException) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "push-registration"
    }
}
