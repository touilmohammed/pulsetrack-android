package corsica.universita.pulsetrack.data.remote

import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST

/** days : 0 = dimanche … 6 = samedi, comme Date.getDay() dans la PWA et sur le serveur. */
@Serializable
data class ReminderDto(val days: List<Int>, val hour: Int, val timeZone: String)

@Serializable
data class FcmRegistration(val token: String, val reminder: ReminderDto)

@Serializable
data class FcmToken(val token: String)

@Serializable
data class PushTestResult(val sent: Int = 0, val expired: Int = 0, val failed: Int = 0)

@Serializable
data class ApiError(val error: String = "")

/** Les rappels Android : le même stockage et la même tâche horaire que les rappels de la PWA. */
interface PushApi {

    @POST("api/push/fcm")
    suspend fun register(
        @Header("X-Device-Id") deviceId: String,
        @Body body: FcmRegistration,
    ): Response<ResponseBody>

    // DELETE avec un corps : Retrofit l'interdit avec @DELETE, @HTTP le permet.
    @HTTP(method = "DELETE", path = "api/push/fcm", hasBody = true)
    suspend fun unregister(
        @Header("X-Device-Id") deviceId: String,
        @Body body: FcmToken,
    ): Response<ResponseBody>

    @POST("api/push/test")
    suspend fun test(@Header("X-Device-Id") deviceId: String): Response<PushTestResult>
}
