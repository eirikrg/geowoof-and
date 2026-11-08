package com.geowoof.core.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.geowoof.core.common.Dispatcher
import com.geowoof.core.common.GeoWoofDispatchers
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

class CompressedImage(val bytes: ByteArray, val contentType: String)

/** Prepares a picked image for upload: at most 2048 px, JPEG 85 %. */
interface ImageCompressor {
    suspend fun compress(contentUri: String): Outcome<CompressedImage>

    /** An image that was already compressed and saved on the device. */
    suspend fun readCompressed(path: String): Outcome<CompressedImage>
}

/** The sizes of the compression, separated from the bitmaps so that they can be tested on the JVM. */
internal object ImageSizing {
    const val MAX_SIZE = 2048

    /** The largest power of two that keeps the decoded image at least as big as the final one. */
    fun inSampleSize(width: Int, height: Int, maxSize: Int = MAX_SIZE): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxSize) {
            sample *= 2
        }

        return sample
    }

    /** The final size, or the same one for images that already fit: they are never enlarged. */
    fun fitWithin(width: Int, height: Int, maxSize: Int = MAX_SIZE): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxSize) {
            return width to height
        }

        val scale = maxSize.toFloat() / longest
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}

/**
 * Writing a new JPEG from the pixels leaves the EXIF block behind, so its orientation has to be
 * applied here. The server removes the metadata again whatever it receives.
 */
internal class BitmapImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(GeoWoofDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ImageCompressor {
    override suspend fun compress(contentUri: String): Outcome<CompressedImage> = withContext(ioDispatcher) {
        try {
            val decoded = decode(contentUri)
            if (decoded == null) {
                Outcome.Failure(AppError.Api(status = HTTP_UNPROCESSABLE, code = INVALID_IMAGE))
            } else {
                Outcome.Success(CompressedImage(encode(decoded), JPEG))
            }
        } catch (exception: IOException) {
            Outcome.Failure(AppError.Unexpected(exception.message))
        } catch (exception: SecurityException) {
            Outcome.Failure(AppError.Unexpected(exception.message))
        }
    }

    override suspend fun readCompressed(path: String): Outcome<CompressedImage> = withContext(ioDispatcher) {
        try {
            Outcome.Success(CompressedImage(File(path).readBytes(), JPEG))
        } catch (exception: IOException) {
            Outcome.Failure(AppError.Unexpected(exception.message))
        }
    }

    private fun decode(contentUri: String): Bitmap? {
        val uri = contentUri.toUri()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val hasPixels = bounds.outWidth > 0 && bounds.outHeight > 0
        val options = BitmapFactory.Options()
        if (hasPixels) {
            options.inSampleSize = ImageSizing.inSampleSize(bounds.outWidth, bounds.outHeight)
        }

        val bitmap = if (hasPixels) open(uri)?.use { BitmapFactory.decodeStream(it, null, options) } else null
        return bitmap?.upright(orientationOf(uri))?.scaledToFit()
    }

    private fun open(uri: Uri): InputStream? = context.contentResolver.openInputStream(uri)

    private fun orientationOf(uri: Uri): Int = open(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL

    private fun encode(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun Bitmap.upright(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)

            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(HALF_TURN)

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(QUARTER_TURN)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(QUARTER_TURN)

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-QUARTER_TURN)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-QUARTER_TURN)

            else -> return this
        }

        return replaceWith(Bitmap.createBitmap(this, 0, 0, width, height, matrix, true))
    }

    private fun Bitmap.scaledToFit(): Bitmap {
        val (targetWidth, targetHeight) = ImageSizing.fitWithin(width, height)
        return if (targetWidth == width && targetHeight == height) {
            this
        } else {
            replaceWith(scale(targetWidth, targetHeight))
        }
    }

    private fun Bitmap.replaceWith(other: Bitmap): Bitmap {
        if (other !== this) {
            recycle()
        }

        return other
    }

    private companion object {
        const val JPEG = "image/jpeg"
        const val JPEG_QUALITY = 85
        const val QUARTER_TURN = 90f
        const val HALF_TURN = 180f
        const val HTTP_UNPROCESSABLE = 422
        const val INVALID_IMAGE = "media.invalid_image"
    }
}
