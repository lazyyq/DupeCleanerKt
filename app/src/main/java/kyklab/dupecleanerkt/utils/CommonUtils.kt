package kyklab.dupecleanerkt.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.*

inline fun <T : Cursor> T.untilLast(block: (T) -> Unit): T {
    this.use {
        if (moveToFirst()) {
            do {
                block(this)
            } while (moveToNext())
        }
    }
    return this
}

fun getAlbumArtUri(albumId: Long): Uri {
    val artworkUri =
        Uri.parse("content://media/external/audio/albumart") // MediaProvider.ALBUMART_URI
    return ContentUris.withAppendedId(artworkUri, albumId)
}

val Context.lbm: LocalBroadcastManager
    get() = LocalBroadcastManager.getInstance(this)

fun <E> Collection<E>.toArrayList() = (this as? ArrayList<E>) ?: ArrayList(this)
