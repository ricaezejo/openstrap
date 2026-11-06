package org.openstrap.app.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

private const val TAG = "GpxShare"

// The FileProvider's authority after the package name, as in the manifest; export/ may not reach ui/ for StoryFiles's copy.
private const val AUTHORITY_SUFFIX = ".share"

/** A workout's GPX as a cached file another app can read for a moment, through the share FileProvider. */
internal object GpxShare {
    /** Named like the story picture: after the workout's start (epoch seconds). */
    fun fileName(workoutStart: Long) = "OpenStrap_$workoutStart.gpx"

    /** [gpx] written over [fileName] in the share cache; null when it can't be written. Blocks on the file write. */
    fun forSharing(context: Context, fileName: String, gpx: String): Uri? = runCatching {
        val file = File(File(context.cacheDir, "share").apply { mkdirs() }, fileName)
        file.writeText(gpx)
        FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }.onFailure { Log.w(TAG, "writing the route to share failed", it) }.getOrNull()

    /** Opens the share sheet for [uri]. */
    fun send(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).setType("application/gpx+xml").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).also { it.clipData = ClipData.newRawUri(null, uri) }
        context.startActivity(Intent.createChooser(send, null))
    }
}
