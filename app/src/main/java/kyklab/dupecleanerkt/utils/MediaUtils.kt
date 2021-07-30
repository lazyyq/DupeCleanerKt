package kyklab.dupecleanerkt.utils

import android.content.Context
import android.media.MediaScannerConnection

fun Context.scanMediaFiles(
    path: String,
    callbackOnEachScanComplete: Boolean = false,
    callback: MediaScannerConnection.OnScanCompletedListener?
) {
    scanMediaFiles(arrayOf(path), callbackOnEachScanComplete, callback)
}

fun Context.scanMediaFiles(
    paths: Array<String>,
    callbackOnEachScanComplete: Boolean = false,
    callback: MediaScannerConnection.OnScanCompletedListener?
) {
    var scanned = 0
    MediaScannerConnection.scanFile(this, paths, null) { path, uri ->
        if (callbackOnEachScanComplete || ++scanned == paths.size) {
            callback?.onScanCompleted(path, uri)
        }
    }
}