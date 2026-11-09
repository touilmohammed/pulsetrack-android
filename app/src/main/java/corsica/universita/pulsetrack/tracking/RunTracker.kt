package corsica.universita.pulsetrack.tracking

import android.location.Location
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Une course terminée, prête à préremplir le formulaire de séance. */
data class FinishedRun(
    val startedAtMillis: Long,
    val durationMin: Int,
    val distanceKm: Double,
    val startLat: Double?,
    val startLng: Double?,
)

data class RunState(
    val running: Boolean = false,
    val startedAtMillis: Long = 0,
    val distanceM: Double = 0.0,
    /** Mesures retenues : l'écran montre que le GPS répond. */
    val fixes: Int = 0,
    val lastAccuracyM: Float? = null,
    val start: Location? = null,
    val last: Location? = null,
    val finished: FinishedRun? = null,
)

/**
 * L'état de la course, partagé par le service (qui écrit) et l'écran (qui lit). Il vit dans le process :
 * tant que le foreground service tourne, l'OS garde ce process en vie, même écran éteint.
 */
object RunTracker {

    /** Au-delà, la mesure est trop floue pour une distance : on l'écarte. */
    private const val MAX_ACCURACY_M = 30f
    /** 12 m/s, soit plus de 43 km/h : un saut du GPS, pas un coureur. */
    private const val MAX_SPEED_MPS = 12.0

    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    fun start(now: Long = System.currentTimeMillis()) {
        _state.value = RunState(running = true, startedAtMillis = now)
    }

    fun onLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return
        _state.update { current ->
            if (!current.running) return@update current
            val previous = current.last ?: return@update current.copy(
                start = location, last = location, fixes = 1, lastAccuracyM = accuracyOf(location),
            )
            val step = previous.distanceTo(location).toDouble()
            val seconds = (location.time - previous.time) / 1000.0
            if (seconds > 0 && step / seconds > MAX_SPEED_MPS) return@update current
            current.copy(
                distanceM = current.distanceM + step,
                last = location,
                fixes = current.fixes + 1,
                lastAccuracyM = accuracyOf(location),
            )
        }
    }

    fun stop(now: Long = System.currentTimeMillis()) {
        _state.update { current ->
            if (!current.running) return@update current
            val minutes = ceil((now - current.startedAtMillis) / 60_000.0).toInt().coerceAtLeast(1)
            current.copy(
                running = false,
                finished = FinishedRun(
                    startedAtMillis = current.startedAtMillis,
                    durationMin = minutes,
                    distanceKm = (current.distanceM / 10.0).roundToLong() / 100.0,
                    startLat = current.start?.latitude,
                    startLng = current.start?.longitude,
                ),
            )
        }
    }

    /** Course enregistrée ou abandonnée : on repart de zéro. */
    fun reset() {
        _state.value = RunState()
    }

    private fun accuracyOf(location: Location) = if (location.hasAccuracy()) location.accuracy else null
}
