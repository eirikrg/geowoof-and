package com.geowoof.core.data.media

import android.content.Context
import com.geowoof.core.common.Dispatcher
import com.geowoof.core.common.GeoWoofDispatchers
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.flatMap
import com.geowoof.core.model.map
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * The photos of a saved walk, until they are uploaded. The URIs of the Photo Picker stop working
 * soon, and a walk may be uploaded much later: the files stay private to the app.
 */
interface WalkPhotoStore {
    /** The paths of the saved copies, in the same order. If one of them fails, nothing is kept. */
    suspend fun store(walkId: String, contentUris: List<String>): Outcome<List<String>>

    suspend fun delete(walkId: String)

    suspend fun deleteAll()
}

internal class FilesWalkPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compressor: ImageCompressor,
    @Dispatcher(GeoWoofDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : WalkPhotoStore {
    private val root: File get() = File(context.filesDir, DIRECTORY)

    override suspend fun store(walkId: String, contentUris: List<String>): Outcome<List<String>> {
        var stored: Outcome<List<String>> = Outcome.Success(emptyList())
        contentUris.forEachIndexed { index, uri ->
            stored = stored.flatMap { paths ->
                compressor.compress(uri).flatMap { write(walkId, index, it) }.map { paths + it }
            }
        }
        if (stored is Outcome.Failure) delete(walkId)
        return stored
    }

    private suspend fun write(walkId: String, index: Int, image: CompressedImage): Outcome<String> =
        withContext(ioDispatcher) {
            try {
                val directory = File(root, walkId).apply { mkdirs() }
                val file = File(directory, "$index.jpg")
                file.writeBytes(image.bytes)
                Outcome.Success(file.path)
            } catch (exception: IOException) {
                Outcome.Failure(AppError.Unexpected(exception.message))
            }
        }

    override suspend fun delete(walkId: String) {
        withContext(ioDispatcher) { File(root, walkId).deleteRecursively() }
    }

    override suspend fun deleteAll() {
        withContext(ioDispatcher) { root.deleteRecursively() }
    }

    private companion object {
        const val DIRECTORY = "walk_photos"
    }
}
