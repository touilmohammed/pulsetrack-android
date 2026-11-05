package corsica.universita.pulsetrack.data

/** Les types acceptés par l'API de la PWA. [apiValue] est la valeur stockée et envoyée au serveur. */
enum class SessionType(val apiValue: String, val label: String) {
    COURSE("course", "Course"),
    VELO("velo", "Vélo"),
    MARCHE("marche", "Marche"),
    MUSCULATION("musculation", "Musculation"),
    AUTRE("autre", "Autre");

    companion object {
        /** Une valeur inconnue (écrite par une future version, par exemple) ne doit pas faire planter. */
        fun fromApi(value: String): SessionType = entries.find { it.apiValue == value } ?: AUTRE
    }
}

/** Une séance, telle que l'écran la connaît. */
data class Session(
    val id: String,
    val type: SessionType,
    val dateMillis: Long,
    val durationMin: Int,
    val distanceKm: Double?,
    val note: String,
    /** Exemple importé au premier lancement : jamais envoyé au serveur tant qu'il n'est pas modifié. */
    val isExample: Boolean = false,
)
