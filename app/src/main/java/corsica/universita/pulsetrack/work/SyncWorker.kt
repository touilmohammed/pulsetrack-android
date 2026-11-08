package corsica.universita.pulsetrack.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import corsica.universita.pulsetrack.AppContainer
import java.io.IOException

/**
 * Créé et lancé par WorkManager, jamais par l'app : il tourne même app fermée ou tuée.
 * Il n'a pas d'écran : il passe par le repository, comme les écrans.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val report = AppContainer.repository.syncPending()
        Log.i(TAG, "Envoyées : ${report.sent}, refusées : ${report.rejected}, à réessayer : ${report.retryLater}")
        if (report.retryLater) {
            Result.retry() // serveur indisponible ou trop sollicité : délai doublé à chaque essai
        } else {
            AppContainer.repository.refresh() // le serveur est joignable : on relit sa version
            Result.success()
        }
    } catch (e: IOException) {
        Log.i(TAG, "Réseau perdu pendant l'envoi : nouvel essai plus tard", e)
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "sync"
        private const val TAG = "SyncWorker"
    }
}
