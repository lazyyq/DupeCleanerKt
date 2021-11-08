package kyklab.dupecleanerkt.dupemanager

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kyklab.dupecleanerkt.data.Music
import kyklab.dupecleanerkt.utils.scanMediaFiles
import kyklab.dupecleanerkt.utils.untilLast
import java.io.File
import java.util.*


class DupeManager(
    private val context: Context,
    private val scope: CoroutineScope,
    var path: String,
    var matchMode: MatchMode
) {
    companion object {
        private val TAG = DupeManager::class.java.simpleName
    }

    enum class MatchMode {
        TITLE,
        TITLE_ARTIST,
        TITLE_ARTIST_ALBUM,
    }

    // List of files found under selected directory, ready to be analyzed for duplicates
    // val foundFiles: MutableList<Music> = LinkedList()

    // Map of specific file and list of its duplicates
    private val hashMap: HashMap<String, MutableList<Music>> = HashMap()

    // List of lists containing duplicates
    var dupeList: ArrayList<MutableList<Music>> = ArrayList(100)

    var totalScanned = 0 // Number of total analyzed files
    var totalDuplicates = 0 // Number of total duplicates

    fun scan(runMediaScannerFirst: Boolean = false, callback: ScanCompletedCallback? = null) {
        if (runMediaScannerFirst) {
            context.scanMediaFiles(path) { path, uri ->
                scanInternal(callback)
            }
        } else {
            scanInternal(callback)
        }
    }

    private fun scanInternal(callback: ScanCompletedCallback? = null) {
        scope.launch(Dispatchers.IO) {
            /* Query mediastore db for music files and add to found music list */

            dupeList.clear()
            hashMap.clear()
            totalScanned = 0
            totalDuplicates = 0

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.AlbumColumns.ARTIST,
                    MediaStore.Audio.AlbumColumns.ALBUM,
                    MediaStore.Audio.AlbumColumns.ALBUM_ID,
                    MediaStore.Audio.AudioColumns.DURATION,
                    MediaStore.Audio.Media.DATE_MODIFIED
                ),
                "${MediaStore.Audio.Media.DATA} LIKE '$path/%.mp3'",
                null,
                "${MediaStore.Audio.Media.DATA} ASC"
            )?.use { cursor ->
                val dataColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val titleColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.AlbumColumns.ARTIST)
                val albumColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.AlbumColumns.ALBUM)
                val albumIdColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.AlbumColumns.ALBUM_ID)
                val durationColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION)
                val dateModifiedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                dupeList.ensureCapacity(cursor.count)

                cursor.untilLast {
                    val musicPath = it.getString(dataColumn)
                    if (File(musicPath).exists()) {
                        val music = Music(
                            path = musicPath,
                            title = it.getString(titleColumn),
                            artist = it.getString(artistColumn),
                            album = it.getString(albumColumn),
                            albumId = it.getLong(albumIdColumn),
                            duration = it.getLong(durationColumn),
                            dateModified = it.getLong(dateModifiedColumn),
                        )

                        val key = generateHashKey(music)
                        if (hashMap.containsKey(key)) {
                            hashMap[key]?.add(music)
                        } else {
                            val list = ArrayList<Music>()
                            list.add(music)
                            dupeList.add(list)
                            hashMap[key] = list
                        }
                        // updater.accept(music)
                        ++totalScanned
                        // if (updater != null) updater.accept(music)
                    } // Check file exists
                } // Cursor
            } // Mediastore query

            /* Query done */

            dupeList.apply {
                removeIf { it.size <= 1 }
                trimToSize()
                forEach { totalDuplicates += it.size }
            }

            callback?.onScanDone(dupeList, totalScanned, totalDuplicates)
        }
    }

    fun interface ScanCompletedCallback {
        fun onScanDone(duplicates: List<List<Music>>, totalScanned: Int, totalDuplicates: Int)
    }

    private fun generateHashKey(music: Music): String {
        return when (matchMode) {
            MatchMode.TITLE -> music.title
            MatchMode.TITLE_ARTIST -> "${music.title}\n\n${music.artist}"
            MatchMode.TITLE_ARTIST_ALBUM -> "${music.title}\n\n${music.artist}\n\n${music.album}"
        }
    }
}