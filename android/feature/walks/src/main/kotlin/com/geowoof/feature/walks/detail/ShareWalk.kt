package com.geowoof.feature.walks.detail

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.Walk
import com.geowoof.core.ui.WalkShareCard
import com.geowoof.core.ui.formattedIn
import com.geowoof.feature.walks.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Shows what is going to be shared, and shares exactly that: it is drawn with Compose and captured as an image. */
@Composable
internal fun ShareWalkDialog(
    walk: Walk,
    lines: List<List<GeoPoint>>,
    mapSnapshot: ImageBitmap?,
    petNames: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capture = rememberGraphicsLayer()
    val chooserTitle = stringResource(R.string.feature_walks_share_chooser)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.feature_walks_share_title)) },
        text = {
            WalkShareCard(
                lines = lines,
                mapSnapshot = mapSnapshot,
                title = walk.startedAt.formattedIn(walk.timeZone),
                subtitle = petNames,
                stats = walk.stats,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        capture.record { this@drawWithContent.drawContent() }
                        drawLayer(capture)
                    },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val image = capture.toImageBitmap().asAndroidBitmap()
                        val file = withContext(Dispatchers.IO) { writeSharedImage(context, image) }
                        val uri = FileProvider.getUriForFile(context, shareAuthority(context), file)
                        context.startActivity(Intent.createChooser(shareIntent(uri), chooserTitle))
                        onDismiss()
                    }
                },
            ) {
                Text(stringResource(R.string.feature_walks_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_walks_share_close)) }
        },
    )
}

/** Only the last one is kept, in the cache, and other apps can only read it through the link that is shared. */
internal fun writeSharedImage(context: Context, image: Bitmap): File {
    val directory = File(context.cacheDir, "shared_walks").apply { mkdirs() }
    val file = File(directory, "walk.png")
    // What a graphics layer captures may live in the GPU, and that cannot be compressed in all versions.
    val software = image.copy(Bitmap.Config.ARGB_8888, false)
    file.outputStream().use { software.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    return file
}

internal fun shareAuthority(context: Context): String = "${context.packageName}.walks.fileprovider"

internal fun shareIntent(image: Uri): Intent = Intent(Intent.ACTION_SEND)
    .setType("image/png")
    .putExtra(Intent.EXTRA_STREAM, image)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

private const val PNG_QUALITY = 100
