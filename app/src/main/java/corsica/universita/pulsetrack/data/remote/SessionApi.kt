package corsica.universita.pulsetrack.data.remote

import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

/** Le format de l'API de la PWA (server/api.mjs). Distinct de SessionEntity : chacun évolue seul. */
@Serializable
data class SessionDto(
    val id: String,
    val type: String,
    /** ISO 8601, comme Date.toISOString() côté navigateur. */
    val date: String,
    val durationMin: Int,
    val distanceKm: Double? = null,
    val note: String = "",
    val location: LocationDto? = null,
    val version: Int? = null,
)

@Serializable
data class LocationDto(val lat: Double, val lng: Double, val accuracy: Int? = null)

@Serializable
data class SessionEnvelope(val session: SessionDto)

@Serializable
data class SessionList(val sessions: List<SessionDto>)

/** Retrofit génère le code des requêtes ; chaque fonction suspend s'exécute hors du main thread. */
interface SessionApi {

    @GET("api/sessions")
    suspend fun list(@Header("X-Device-Id") deviceId: String): SessionList

    /**
     * PUT crée la séance si elle n'existe pas encore sur le serveur. Un en-tête nul n'est pas envoyé.
     * X-If-Match double If-Match : le CDN de Netlify retire If-Match avant la Function (doc de la PWA, § 6.7).
     * Response<…> plutôt que le corps seul : un 412 porte la copie du serveur, à lire dans errorBody().
     */
    @PUT("api/sessions/{id}")
    suspend fun put(
        @Path("id") id: String,
        @Header("X-Device-Id") deviceId: String,
        @Header("Idempotency-Key") opId: String,
        @Header("If-Match") ifMatch: String?,
        @Header("X-If-Match") xIfMatch: String?,
        @Body body: SessionDto,
    ): Response<SessionEnvelope>

    @DELETE("api/sessions/{id}")
    suspend fun delete(
        @Path("id") id: String,
        @Header("X-Device-Id") deviceId: String,
        @Header("Idempotency-Key") opId: String,
    ): Response<ResponseBody>
}
