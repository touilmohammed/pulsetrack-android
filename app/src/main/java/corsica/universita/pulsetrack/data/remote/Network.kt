package corsica.universita.pulsetrack.data.remote

import android.content.Context
import android.util.Log
import corsica.universita.pulsetrack.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Champs inconnus ignorés : le serveur peut ajouter des champs (createdAt, location…) sans casser l'app. */
val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Un seul OkHttpClient par process : il partage connexions et cache entre toutes les requêtes. */
fun okHttp(context: Context): OkHttpClient =
    OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 10L * 1024 * 1024))
        .addInterceptor(
            HttpLoggingInterceptor { message -> Log.d("PulseTrackHttp", message) }
                .apply { level = HttpLoggingInterceptor.Level.BASIC },
        )
        // Sans délais, une connexion lente bloquerait le worker plusieurs minutes.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

fun sessionApi(client: OkHttpClient): SessionApi =
    Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SessionApi::class.java)
