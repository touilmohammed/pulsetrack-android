package corsica.universita.pulsetrack.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinksTest {

    private val base = "https://pulsetrackisco.netlify.app"

    @Test
    fun `la page d'accueil ouvre le journal`() {
        assertEquals(LinkTarget.Journal, AppLinks.parse("$base/"))
        assertEquals(LinkTarget.Journal, AppLinks.parse(base))
    }

    @Test
    fun `un lien de seance ouvre son detail`() {
        val id = "3f2b8c1e-9d4a-4e7b-a1c2-5d6e7f8a9b0c"
        assertEquals(LinkTarget.SessionDetail(id), AppLinks.parse("$base/?session=$id"))
        assertEquals(LinkTarget.SessionDetail(id), AppLinks.parse("$base/?utm_source=x&session=$id"))
    }

    @Test
    fun `le raccourci du manifest ouvre une nouvelle seance`() {
        assertEquals(LinkTarget.NewSession, AppLinks.parse("$base/#session-form"))
    }

    @Test
    fun `le lien partage revient a la meme seance`() {
        val id = "ex-1"
        assertEquals(LinkTarget.SessionDetail(id), AppLinks.parse(AppLinks.sessionUrl(id)))
    }

    @Test
    fun `tout le reste est refuse`() {
        listOf(
            null,
            "",
            "pas une url",
            "http://pulsetrackisco.netlify.app/?session=abc", // pas https
            "https://pulsetrackisco.netlify.app.evil.com/?session=abc", // autre hôte
            "https://evil.com/?session=abc",
            "$base/api/sessions", // un autre chemin
            "$base/?session=", // identifiant vide
            "$base/?session=" + "a".repeat(65), // trop long
            "$base/?session=..%2F..%2Fetc", // caractères interdits
            "$base/?session=%3Cscript%3E",
        ).forEach { assertNull(it, AppLinks.parse(it)) }
    }
}
