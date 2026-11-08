package corsica.universita.pulsetrack.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Pour l'affichage seulement (le bandeau « hors ligne ») : la décision d'envoyer revient à WorkManager.
 * VALIDATED et non INTERNET : un Wi-Fi d'hôtel avant son portail annonce Internet sans y donner accès.
 */
class NetworkMonitor(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val isOnline: Flow<Boolean> = callbackFlow {
        fun current() = connectivity.getNetworkCapabilities(connectivity.activeNetwork).validated()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.validated())
            }

            override fun onLost(network: Network) {
                trySend(current())
            }
        }
        trySend(current())
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun NetworkCapabilities?.validated() =
        this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}
