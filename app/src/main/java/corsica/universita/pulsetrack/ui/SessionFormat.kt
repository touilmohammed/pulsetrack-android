package corsica.universita.pulsetrack.ui

import corsica.universita.pulsetrack.data.Session
import corsica.universita.pulsetrack.data.SessionLocation
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

/** « 42.30044, 9.15009 (± 12 m) », comme dans le détail de la PWA. */
fun locationText(location: SessionLocation): String =
    "${location.lat}, ${location.lng}" + (location.accuracy?.let { " (± $it m)" } ?: "")

/** Le lien de la PWA : une carte OpenStreetMap centrée sur la séance, sans clé ni compte. */
fun mapUrl(location: SessionLocation): String =
    "https://www.openstreetmap.org/?mlat=${location.lat}&mlon=${location.lng}#map=15/${location.lat}/${location.lng}"

/** Le texte partagé : le résumé, puis le lien de la séance (ouvert par l'app si installée, sinon par la PWA). */
fun shareText(session: Session): String = buildString {
    append("${session.type.label} · ${sessionMeta(session)}")
    if (session.note.isNotEmpty()) append('\n').append(session.note)
    append('\n').append(corsica.universita.pulsetrack.links.AppLinks.sessionUrl(session.id))
}

/** « 4 oct. 2026 à 09:30 · 45 min · 8,2 km », comme dans le journal de la PWA. */
fun sessionMeta(session: Session): String = buildList {
    add(formatDate(session.dateMillis))
    add("${session.durationMin} min")
    session.distanceKm?.let { add("${formatDistance(it)} km") }
    if (session.location != null) add("avec position")
    if (session.photoPath != null) add("avec photo")
}.joinToString(" · ")
