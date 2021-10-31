package kyklab.dupecleanerkt.fileoprations

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import com.anggrayudi.storage.file.DocumentFileCompat
import kyklab.dupecleanerkt.R
import kyklab.dupecleanerkt.utils.lbm

class FileDeleteService : FileOperationService() {
    companion object {
        private const val TAG = "FileDeleteService"

        const val EXTRA_SRC = "extra_src"

        // Processed one file, regardless of the result
        const val ACTION_FILE_PROCESSED = "file_processed"

        // All files have been dealt with
        const val ACTION_OPERATION_DONE = "operation_done"

        const val EXTRA_TOTAL_PROCESSED = "extra_total"
        const val EXTRA_SUCCESS = "extra_success"
        const val EXTRA_FAILURE = "extra_failure"

        private const val CHANNEL_ID = "channel_file_delete"
        private const val CHANNEL_NAME = "Delete"

        private const val NOTIFICATION_ID = 101

        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500
    }

    private var lastNotificationProgressUpdate = 0L
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    override val foregroundNotificationBuilder: Notification.Builder
        get() = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Deleting files")
            .setOngoing(true)

    override val notificationChannelId: String
        get() = CHANNEL_ID
    override val notificationChannelName: String
        get() = CHANNEL_NAME
    override val notificationId: Int
        get() = NOTIFICATION_ID

    private lateinit var src: ArrayList<String>

    // Counts of processed files
    private var totalProcessed = 0
    private var success = 0
    private var failure = 0

    // How many broadcasts have been sent, for debugging purpose
    private var totalSent = 0

    override suspend fun operate(intent: Intent) {
        Log.e(TAG, "operate() start")
        getFlags(intent)
        doDelete()
    }

    private fun getFlags(intent: Intent) {
        src = intent.getStringArrayListExtra(EXTRA_SRC)
            ?: throw InsufficientDeleteArgumentsException()
    }

    private fun doDelete() {
        // Set progress for progress bar in notification
        updateProgressNotification()

        src.forEach { path ->
            try {
                val file = DocumentFileCompat.fromFullPath(this, path)!!
                if (file.delete()) {
                    ++success
                } else {
                    ++failure
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

        finishOperation()
    }

    private fun notifyProgress() {
        ++totalSent
        val intent = Intent(ACTION_FILE_PROCESSED).apply {
            putExtra(EXTRA_TOTAL_PROCESSED, totalProcessed)
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_FAILURE, failure)
        }
        this@FileDeleteService.lbm.sendBroadcast(intent)
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
            "total: $totalProcessed, success: $success, failure: $failure"
        )
        ++totalSent
        val intent = Intent(ACTION_OPERATION_DONE).apply {
            putExtra(EXTRA_TOTAL_PROCESSED, totalProcessed)
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_FAILURE, failure)
        }
        this@FileDeleteService.lbm.sendBroadcast(intent)
    }

    private class InsufficientDeleteArgumentsException : Exception()
}