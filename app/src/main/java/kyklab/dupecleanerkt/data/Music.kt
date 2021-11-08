package kyklab.dupecleanerkt.data

import android.icu.text.SimpleDateFormat
import java.util.*

data class Music(
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val dateModified: Long,
) {
    companion object {

        private val format = SimpleDateFormat()
        private val calendar = Calendar.getInstance()
    }

    val durationString = run {
        var seconds = duration / 1000
        val minutes = seconds / 60
        seconds %= 60
        "$minutes:${if (seconds < 10) "0$seconds" else seconds}"
    }

    val dateModifiedString: String = run {
        calendar.timeInMillis = dateModified * 1000
        format.format(calendar.time)
    }

    override fun toString() =
        "Title: $title, Artist: $artist, Album: $album, Duration: $durationString, Date modified: $dateModifiedString, Path: $path"
}
