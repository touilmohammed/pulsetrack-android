package corsica.universita.pulsetrack.ui

import corsica.universita.pulsetrack.data.Session
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy 'à' HH:mm", Locale.FRENCH)
private val distance = NumberFormat.getNumberInstance(Locale.FRENCH).apply {
    maximumFractionDigits = 2
    isGroupingUsed = false
}

fun formatDate(millis: Long): String =
    dateTime.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** « 8,2 » et non « 8.2 », « 38 » et non « 38.0 ». */
fun formatDistance(km: Double): String = distance.format(km)

/** « 4 oct. 2026 à 09:30 · 45 min · 8,2 km », comme dans le journal de la PWA. */
fun sessionMeta(session: Session): String = buildList {
    add(formatDate(session.dateMillis))
    add("${session.durationMin} min")
    session.distanceKm?.let { add("${formatDistance(it)} km") }
}.joinToString(" · ")
