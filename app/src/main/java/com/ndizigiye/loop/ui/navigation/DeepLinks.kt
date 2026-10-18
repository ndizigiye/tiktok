package com.ndizigiye.loop.ui.navigation

import android.net.Uri

// Must match the intent filter on MainActivity in AndroidManifest.xml.
private const val DEEP_LINK_SCHEME = "loop"
private const val VIDEO_HOST = "video"

/**
 * The video id of a `loop://video/{id}` link (the "Open in Loop" button on the server's
 * share page), or null when [uri] is not one.
 */
fun videoIdFromDeepLink(uri: Uri?): Long? {
    if (uri == null || uri.scheme != DEEP_LINK_SCHEME || uri.host != VIDEO_HOST) return null
    return uri.pathSegments.singleOrNull()?.toLongOrNull()?.takeIf { it > 0 }
}
