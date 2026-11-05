package corsica.universita.pulsetrack.data

import kotlin.math.roundToLong

const val NOTE_MAX = 500
const val DURATION_MAX = 24 * 60
const val DISTANCE_MAX = 1000.0

/** Ce que l'utilisateur a saisi : du texte, tant que ce n'est pas validé. */
data class SessionInput(
    val type: SessionType,
    val dateMillis: Long,
    val durationMin: String,
    val distanceKm: String,
    val note: String,
)

/** Les champs validés, prêts à devenir une [Session]. */
data class ValidSession(
    val type: SessionType,
    val dateMillis: Long,
    val durationMin: Int,
    val distanceKm: Double?,
    val note: String,
)

sealed interface ValidationResult {
    data class Valid(val value: ValidSession) : ValidationResult
    data class Invalid(val errors: List<String>) : ValidationResult
}

/** Mêmes règles que validateSession() de la PWA et du serveur. Fonction pure : testée sans Android. */
fun validateSession(input: SessionInput): ValidationResult {
    val errors = mutableListOf<String>()

    val duration = input.durationMin.trim().toIntOrNull()
    if (duration == null || duration <= 0 || duration > DURATION_MAX) {
        errors += "La durée doit être un nombre entier de minutes, supérieur à 0."
    }

    // Le clavier français propose la virgule : elle vaut un point.
    val distanceText = input.distanceKm.trim().replace(',', '.')
    val distance = if (distanceText.isEmpty()) null else distanceText.toDoubleOrNull()
    val distanceInvalid = distanceText.isNotEmpty() &&
        (distance == null || !distance.isFinite() || distance < 0 || distance > DISTANCE_MAX)
    if (distanceInvalid) errors += "La distance doit être un nombre positif (en km)."

    val note = input.note.trim()
    if (note.length > NOTE_MAX) errors += "La note ne doit pas dépasser $NOTE_MAX caractères."

    if (errors.isNotEmpty()) return ValidationResult.Invalid(errors)
    return ValidationResult.Valid(
        ValidSession(
            type = input.type,
            dateMillis = input.dateMillis,
            durationMin = duration!!,
            distanceKm = distance?.let { (it * 100).roundToLong() / 100.0 },
            note = note,
        ),
    )
}
