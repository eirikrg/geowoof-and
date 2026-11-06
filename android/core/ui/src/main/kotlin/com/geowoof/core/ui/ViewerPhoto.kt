package com.geowoof.core.ui

import androidx.compose.runtime.Immutable
import com.geowoof.core.model.RemoteImage

/** A photo on the server, or still only on the device, as those of a walk or a message that have not been sent. */
@Immutable
data class ViewerPhoto(val image: RemoteImage?, val localPath: String? = null) {
    /** The server has no links while it processes a photo, and then there is nothing to enlarge yet. */
    val canOpen: Boolean get() = localPath != null || image?.mediumUrl != null || image?.thumbUrl != null
}
