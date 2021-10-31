package kyklab.dupecleanerkt.fileoprations

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.anggrayudi.storage.callback.FileCallback
import com.anggrayudi.storage.extension.postToUi
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.copyFileTo
import com.anggrayudi.storage.file.moveFileTo
import kyklab.dupecleanerkt.R
import kyklab.dupecleanerkt.utils.lbm

class FileCopyService/*(
    private val src: List<String>,
    private val dest: String,
    private val basePath: String,
    private val move: Boolean = false,
    private val callback: FileCopyCallback? = null
)*/ : FileOperationService() {

    companion object {
        private const val TAG = "FileCopyService"

        const val ACTION_COPY = "action_copy"
        const val ACTION_MOVE = "action_move"
        const val EXTRA_SRC = "extra_src"
        const val EXTRA_DEST = "extra_dest"
        const val EXTRA_BASEPATH = "extra_basepath"

        // Processed one file, regardless of the result
        const val ACTION_FILE_PROCESSED = "file_processed"

        // All files have been dealt with
        const val ACTION_OPERATION_DONE = "operation_done"

        const val EXTRA_TOTAL_PROCESSED = "extra_total"
        const val EXTRA_SUCCESS = "extra_success"
        const val EXTRA_FAILURE = "extra_failure"
        const val EXTRA_SKIPPED = "extra_skipped"
        const val EXTRA_OVERRIDE = "extra_override"

        private const val CHANNEL_ID = "channel_file_copy"
        private const val CHANNEL_NAME = "Copy"

        private const val NOTIFICATION_ID = 100

        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500
    }

    private var lastNotificationProgressUpdate = 0L
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    override val foregroundNotificationBuilder: Notification.Builder
        get() = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Copying files")
            .setOngoing(true)

    override val notificationChannelId: String
        get() = CHANNEL_ID
    override val notificationChannelName: String
        get() = CHANNEL_NAME
    override val notificationId: Int
        get() = NOTIFICATION_ID

    private lateinit var simpleStorageCallback: FileCallback

    private lateinit var src: ArrayList<String>
    private lateinit var dest: String
    private lateinit var basePath: String // Directory where we selected sources to copy
    private var move = false // Copy mode - copy or move
    private var override = false // Override if a file with the same name already exists

    // Counts of processed files
    private var totalProcessed = 0
    private var success = 0
    private var failure = 0
    private var skipped = 0

    // How many broadcasts have been sent, for debugging purpose
    private var totalSent = 0

    override suspend fun operate(intent: Intent) {
        getFlags(intent)
        setupCallback()
        doCopy()
    }

    private fun getFlags(intent: Intent) {
        src = intent.getStringArrayListExtra(EXTRA_SRC)
            ?: throw InsufficientCopyArgumentsException()
        dest = intent.getStringExtra(EXTRA_DEST)
            ?: throw InsufficientCopyArgumentsException()
        basePath = intent.getStringExtra(EXTRA_BASEPATH)
            ?: throw InsufficientCopyArgumentsException()
        move = when (intent.action) {
            ACTION_COPY -> false
            ACTION_MOVE -> true
            else -> throw WrongCopyActionException()
        }
        override = intent.extras?.getBoolean(EXTRA_OVERRIDE)
            ?: throw InsufficientCopyArgumentsException()
    }

    private fun setupCallback() {
        simpleStorageCallback = object : FileCallback(lifecycleScope) {
            override fun onFailed(errorCode: ErrorCode) {
                ++failure
            }

            override fun onCompleted(result: Any) {
                ++success
            }

            override fun onConflict(destinationFile: DocumentFile, action: FileConflictAction) {
                if (override) {
                    action.confirmResolution(ConflictResolution.REPLACE)
                } else {
                    action.confirmResolution(ConflictResolution.SKIP)
                    ++skipped
                }
            }
        }
    }

    private fun doCopy() {
        // Set progress for progress bar in notification
        updateProgressNotification()

        src.forEach { path ->
            try {
                // Move selected files to target directory while preserving directory structure
                //
                // Scenario : Scanned directory is /sdcard/Music, targetDir is /sdcard/duplicates,
                // moving "/sdcard/Music/some/path/to/file.mp3" to "/sdcard/duplicates"
                val file = DocumentFileCompat.fromFullPath(this, path)
                val filename = file!!.name!! // "file.mp3"
                // Cut off basepath and filename and leave only "some/path/to"
                val subDir = path.substring(
                    startIndex = basePath.length + 1,
                    endIndex = path.length - filename.length - 1
                )
                val moveDirPath = "$dest/$subDir" // "/sdcard/duplicates/some/path/to"
                if (!DocumentFileCompat.doesExist(this, moveDirPath)) {
                    DocumentFileCompat.mkdirs(this, moveDirPath)
                }
                val moveDir = DocumentFileCompat.fromFullPath(this, moveDirPath)!!

                if (move) {
                    file.moveFileTo(
                        context = this,
                        targetFolder = moveDir,
                        callback = simpleStorageCallback
                    )
                } else {
                    file.copyFileTo(
                        context = this,
                        targetFolder = moveDir,
                        callback = simpleStorageCallback
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ++failure
            } finally {
                ++totalProcessed
                notifyProgress()
                updateProgressNotification()
            }
        }

        // Callbacks are executed on ui thread, while we are currently on a worker thread,
        // causing finishOperation() to sometimes be called earlier than the last callback.
        simpleStorageCallback.uiScope.postToUi {
            finishOperation()
            Log.e(TAG, "Broadcast done, total $totalSent broadcasts sent")
        }

    }

    private fun notifyProgress() {
        ++totalSent
        val intent = Intent(ACTION_FILE_PROCESSED).apply {
            putExtra(EXTRA_TOTAL_PROCESSED, totalProcessed)
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_FAILURE, failure)
            putExtra(EXTRA_SKIPPED, skipped)
        }
        this@FileCopyService.lbm.sendBroadcast(intent)
    }

    private fun updateProgressNotification() {
        val current = System.currentTimeMillis()
        if (current - lastNotificationProgressUpdate >= NOTIFICATION_UPDATE_INTERVAL_MS) {
            lastNotificationProgressUpdate = current
            foregroundNotificationBuilder.setProgress(src.size, totalProcessed, false).build().let {
                notificationManager.notify(NOTIFICATION_ID, it)
            }
        }
    }

    private fun finishOperation() {
        Log.e(
            "finishOperation()",
            "total: $totalProcessed, success: $success, failure: $failure, skipped: $skipped"
        )
        ++totalSent
        val intent = Intent(ACTION_OPERATION_DONE).apply {
            putExtra(EXTRA_TOTAL_PROCESSED, totalProcessed)
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_FAILURE, failure)
            putExtra(EXTRA_SKIPPED, skipped)
        }
        this@FileCopyService.lbm.sendBroadcast(intent)
    }

    private class WrongCopyActionException : Exception()
    private class InsufficientCopyArgumentsException : Exception()
}