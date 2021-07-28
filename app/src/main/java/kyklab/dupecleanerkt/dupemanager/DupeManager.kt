package kyklab.dupecleanerkt.dupemanager

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kyklab.dupecleanerkt.data.Music
import kyklab.dupecleanerkt.utils.untilLast
import java.util.*


class DupeManager(
    private val context: Context,
    private val scope: CoroutineScope,
    var path: String,
    var matchMode: Int,
    var sortMode: Int
) {
    companion object {
        private val TAG = DupeManager::class.java.simpleName

        private fun log(msg: String) = Log.e(TAG, msg)

        const val MATCH_MODE_TITLE = 0
        const val MATCH_MODE_TITLE_ARTIST = 1
        const val MATCH_MODE_TITLE_ARTIST_ALBUM = 2

        const val SORT_MODE_PATH = 0
        const val SORT_MODE_LAST_MODIFIED_DATE = 1
    }

    // List of files found under selected directory, ready to be analyzed for duplicates
    // val foundFiles: MutableList<Music> = LinkedList()

    // List of lists containing duplicates
    private var dupeList: ArrayList<MutableList<Music>> = ArrayList(100)

    // Map of specific file and list of its duplicates
    private val hashMap: HashMap<String, MutableList<Music>> = HashMap()

    private var totalScanned = 0 // Number of total analyzed files
    private var totalDuplicates = 0 // Number of total duplicates

    fun scan(callback: ScanCompletedCallback? = null) {
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
                    val music = Music(
                        path = it.getString(dataColumn),
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
                }
            }

            /* Query done */

            dupeList.apply {
                removeIf { it.size <= 1 }
                trimToSize()
                forEach { totalDuplicates += it.size }
            }

            callback?.onScanDone(dupeList, totalScanned, totalDuplicates)
        }
    }

    fun sort(reverse: Boolean = false, callback: SortCompletedCallback? = null) {
        if (!(sortMode == SORT_MODE_PATH || sortMode == SORT_MODE_LAST_MODIFIED_DATE)) {
            throw RuntimeException("Wrong sort mode")
        }

        scope.launch(Dispatchers.IO) {
            if (sortMode == SORT_MODE_PATH) {
                if (!reverse) {
                    dupeList.forEach { list -> list.sortBy { music -> music.path } }
                } else {
                    dupeList.forEach { list -> list.sortByDescending { music -> music.path } }
                }
            } else /* Modified Date */ {
                if (!reverse) {
                    dupeList.forEach { list -> list.sortBy { music -> music.dateModified } }
                } else {
                    dupeList.forEach { list -> list.sortByDescending { music -> music.dateModified } }
                }
            }
            callback?.onSortDone(dupeList)
        }
    }

    /*
    fun interface Pattern {
        fun compare(m1: Music, m2: Music): Boolean
    }
    */

    fun interface ScanCompletedCallback {
        fun onScanDone(duplicates: List<List<Music>>, totalScanned: Int, totalDuplicates: Int)
    }

    fun interface SortCompletedCallback {
        fun onSortDone(duplicates: List<List<Music>>)
    }

    private fun generateHashKey(music: Music): String {
        return when (matchMode) {
            MATCH_MODE_TITLE -> music.title
            MATCH_MODE_TITLE_ARTIST -> "${music.title}\n\n${music.artist}"
            MATCH_MODE_TITLE_ARTIST_ALBUM -> "${music.title}\n\n${music.artist}\n\n${music.album}"
            else -> throw RuntimeException("Wrong match mode!")
        }
    }
}