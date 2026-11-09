package corsica.universita.pulsetrack.links

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Où mène un lien du site, une fois ouvert dans l'app. */
sealed interface LinkTarget {
    data object Journal : LinkTarget
    data object NewSession : LinkTarget
    data class SessionDetail(val id: String) : LinkTarget
}

/**
 * Les liens de la PWA, que l'app ouvre à sa place (App Link, vérifié par /.well-known/assetlinks.json) :
 *   https://pulsetrackisco.netlify.app/                  → le journal
 *   https://pulsetrackisco.netlify.app/?session=<id>     → le détail d'une séance
 *   https://pulsetrackisco.netlify.app/#session-form     → nouvelle séance (le raccourci du manifest)
 * Un lien vient de l'extérieur : il est traité comme une entrée non fiable, et tout ce qui ne colle pas
 * exactement à ces formes est refusé (null), plutôt que deviné.
 */
object AppLinks {
    const val HOST = "pulsetrackisco.netlify.app"

    /** Un identifiant de séance : UUID ou identifiant d'exemple, jamais plus. */
    private val SESSION_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

    fun parse(link: String?): LinkTarget? {
        val uri = link?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        if (uri.scheme != "https" || uri.host != HOST) return null
        if (uri.rawPath !in setOf(null, "", "/")) return null

        if (uri.fragment == "session-form") return LinkTarget.NewSession
        val session = query(uri.rawQuery)["session"] ?: return LinkTarget.Journal
        return if (SESSION_ID.matches(session)) LinkTarget.SessionDetail(session) else null
    }

    /** Le lien partagé d'une séance : il s'ouvre dans l'app, ou dans la PWA pour qui ne l'a pas. */
    fun sessionUrl(id: String): String = "https://$HOST/?session=${URLEncoder.encode(id, "UTF-8")}"

    private fun query(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            decode(key) to decode(value)
        }

    private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault("")
}
