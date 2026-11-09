package corsica.universita.pulsetrack.tracking

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import corsica.universita.pulsetrack.AppContainer
import corsica.universita.pulsetrack.notif.Notifications

/**
 * Le foreground service du suivi : il mesure en continu, écran éteint, ce qu'une PWA ne peut pas faire
 * (le navigateur suspend la page). En échange, une notification permanente dit à l'utilisateur que le GPS
 * tourne. Type « location » déclaré dans le manifest, exigé depuis Android 14 et vérifié par Google Play.
 */
class TrackingService : Service() {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var lastNotificationAt = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(RunTracker::onLocation)
            // La notification suit la course, sans être redessinée à chaque mesure.
            val now = System.currentTimeMillis()
            if (now - lastNotificationAt >= 5_000) {
                lastNotificationAt = now
                Notifications.updateTracking(this@TrackingService, RunTracker.state.value)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        // L'OS ne relance pas le service s'il le tue : une course interrompue ne reprend pas toute seule.
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // vérifiée par l'écran avant le démarrage ; SecurityException traitée
    private fun start() {
        if (RunTracker.state.value.running) return
        RunTracker.start()
        // Dans les 10 secondes qui suivent startForegroundService, sinon l'OS arrête l'app.
        ServiceCompat.startForeground(
            this,
            Notifications.ID_TRACKING,
            Notifications.tracking(this, RunTracker.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000)
            .setMinUpdateIntervalMillis(1_000)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            Log.i(TAG, "Suivi démarré")
        } catch (e: SecurityException) {
            Log.w(TAG, "Position retirée entre-temps : suivi arrêté", e)
            stop()
        }
    }

    private fun stop() {
        client.removeLocationUpdates(callback)
        val wasRunning = RunTracker.state.value.running
        RunTracker.stop()
        // Une seule fois par course, même si « Arrêter » est touché deux fois.
        if (wasRunning) RunTracker.state.value.finished?.let { run ->
            AppContainer.analytics.runTracked("ok", distanceKm = run.distanceKm, durationMin = run.durationMin)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Suivi arrêté : ${RunTracker.state.value.finished}")
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "corsica.universita.pulsetrack.TRACKING_START"
        const val ACTION_STOP = "corsica.universita.pulsetrack.TRACKING_STOP"
        private const val TAG = "PulseTrackGps"

        /** Depuis l'écran, app au premier plan : depuis Android 12, impossible en arrière-plan. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}
