package kyklab.dupecleanerkt.utils

import android.content.ContentUris
import android.database.Cursor
import android.net.Uri

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
    val artworkUri = Uri.parse("content://media/external/audio/albumart") // MediaProvider.ALBUMART_URI
    val albumArtUri= ContentUris.withAppendedId(artworkUri, albumId)
    return albumArtUri
}