package corsica.universita.pulsetrack.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Les photos : des fichiers dans filesDir/photos, leur chemin dans Room. Comme dans la PWA,
 * elles sont réduites à 1280 px et ne quittent jamais le téléphone (vie privée, quota).
 * Tout le travail sur le disque et les pixels se fait sur Dispatchers.IO, jamais sur le main thread.
 */
class PhotoStore(context: Context) {

    private val app = context.applicationContext
    private val photosDir = File(app.filesDir, "photos")
    private val cameraDir = File(app.cacheDir, "camera")
    private val authority = "${app.packageName}.fileprovider"

    /** Où l'appareil photo du système écrira : un fichier temporaire, exposé par FileProvider. */
    fun newCaptureUri(): Uri {
        cameraDir.mkdirs()
        val file = File(cameraDir, "capture-${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(app, authority, file)
    }

    /** Lit l'image (prise ou choisie), la remet droite, la réduit et l'enregistre. Renvoie son chemin. */
    suspend fun import(source: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(source) ?: return@withContext null
        val upright = rotate(bitmap, orientation(source))
        photosDir.mkdirs()
        val file = File(photosDir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        deleteCapture(source)
        file.absolutePath
    }

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path != null && path.startsWith(photosDir.absolutePath)) File(path).delete()
    }

    suspend fun load(path: String): Bitmap? = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }

    /** Pour partager : une adresse content:// lisible un temps par l'app choisie, jamais le chemin du fichier. */
    fun shareUri(path: String): Uri = FileProvider.getUriForFile(app, authority, File(path))

    private fun decodeScaled(source: Uri): Bitmap? {
        // Première lecture : les dimensions seulement, pour ne pas charger 12 Mpx en mémoire.
        // Avec inJustDecodeBounds, decodeStream renvoie toujours null : seules les dimensions sont remplies.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = app.contentResolver.openInputStream(source) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= MAX_SIZE) sample *= 2
        val decoded = app.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_SIZE.toFloat() / max(decoded.width, decoded.height)
        if (scale >= 1f) return decoded
        return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    }

    /** L'appareil photo enregistre souvent l'image couchée, avec l'angle dans les métadonnées EXIF. */
    private fun orientation(source: Uri): Int =
        app.contentResolver.openInputStream(source)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Le fichier temporaire de l'appareil photo : supprimé après import, ou si la prise de vue est annulée. */
    suspend fun discardCapture(source: Uri) = withContext(Dispatchers.IO) { deleteCapture(source) }

    private fun deleteCapture(source: Uri) {
        if (source.authority != authority) return // image choisie dans la galerie : elle ne nous appartient pas
        source.lastPathSegment?.let { File(cameraDir, it).delete() }
    }

    private companion object {
        const val MAX_SIZE = 1280
    }
}
