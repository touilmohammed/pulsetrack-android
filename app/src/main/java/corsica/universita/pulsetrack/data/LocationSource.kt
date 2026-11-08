package corsica.universita.pulsetrack.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine

/** La position d'une séance : arrondie à 5 décimales (environ 1 m), comme dans la PWA et sur le serveur. */
data class SessionLocation(val lat: Double, val lng: Double, val accuracy: Int?)

/**
 * Le fournisseur fusionné des Play Services (GPS, Wi-Fi et antennes) répond par un callback ;
 * suspendCancellableCoroutine en fait une fonction suspend, annulée avec l'écran qui l'a lancée.
 */
class LocationSource(context: Context) {

    private val app = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(app)

    /** Nulle si aucune permission n'est accordée, ou si aucune position n'est disponible. */
    @SuppressLint("MissingPermission") // vérifiée juste en dessous
    suspend fun current(): SessionLocation? {
        val fine = has(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = has(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return null
        // La précision demandée suit celle que l'utilisateur a accordée : approximative n'est pas un refus.
        val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val cancel = CancellationTokenSource()
        val location = suspendCancellableCoroutine<Location?> { continuation ->
            client.getCurrentLocation(priority, cancel.token)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
            continuation.invokeOnCancellation { cancel.cancel() }
        } ?: return null
        return SessionLocation(
            lat = round5(location.latitude),
            lng = round5(location.longitude),
            accuracy = if (location.hasAccuracy()) location.accuracy.roundToInt() else null,
        )
    }

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    private fun round5(value: Double) = Math.round(value * 100_000) / 100_000.0
}
