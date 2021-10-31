package kyklab.dupecleanerkt.fileoprations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kyklab.dupecleanerkt.utils.lbm
import kyklab.dupecleanerkt.utils.toArrayList

object FileOperations {
    /**
     * Callback for copy operations. Will run on Main thread.
     */
    interface FileCopyCallback {
        fun onProgressUpdate(total: Int, success: Int, failure: Int, skipped: Int)
        fun onOperationDone(total: Int, success: Int, failure: Int, skipped: Int)
    }

    /**
     * Callback for delete operations. Will run on Main thread.
     */
    interface FileDeleteCallback {
        fun onProgressUpdate(total: Int, success: Int, failure: Int)
        fun onOperationDone(total: Int, success: Int, failure: Int)
    }

    private const val TAG = "FileOperations"

    /**
     * Copy or move files or directories to destination.
     * Directory structures are preserved.
     *
     * @param basePath Directory where we selected files to copy. Structures under this directory are preserved.
     * @param move Whether to copy or move files.
     * @param override Whether to override if a file with the same name at destination exists.
     */
    fun copy(
        context: Context,
        src: List<String>,
        dest: String,
        basePath: String,
        move: Boolean = false,
        override: Boolean = false,
        callback: FileCopyCallback? = null
    ) {
        val intent = Intent(context, FileCopyService::class.java).apply {
            action = if (move) {
                FileCopyService.ACTION_MOVE
            } else {
                FileCopyService.ACTION_COPY
            }
            putStringArrayListExtra(FileCopyService.EXTRA_SRC, src.toArrayList())
            putExtra(FileCopyService.EXTRA_DEST, dest)
            putExtra(FileCopyService.EXTRA_BASEPATH, basePath)
            putExtra(FileCopyService.EXTRA_OVERRIDE, override)
        }

        var totalReceived = 0 // How many broadcasts have been received, for debugging purpose
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    FileCopyService.ACTION_FILE_PROCESSED -> {
                        ++totalReceived

                        val total = intent.getIntExtra(FileCopyService.EXTRA_TOTAL_PROCESSED, -1)
                        val success = intent.getIntExtra(FileCopyService.EXTRA_SUCCESS, -1)
                        val failure = intent.getIntExtra(FileCopyService.EXTRA_FAILURE, -1)
                        val skipped = intent.getIntExtra(FileCopyService.EXTRA_SKIPPED, -1)
                        callback?.onProgressUpdate(total, success, failure, skipped)
                    }

                    FileCopyService.ACTION_OPERATION_DONE -> {
                        ++totalReceived

                        val total = intent.getIntExtra(FileCopyService.EXTRA_TOTAL_PROCESSED, -1)
                        val success = intent.getIntExtra(FileCopyService.EXTRA_SUCCESS, -1)
                        val failure = intent.getIntExtra(FileCopyService.EXTRA_FAILURE, -1)
                        val skipped = intent.getIntExtra(FileCopyService.EXTRA_SKIPPED, -1)
                        callback?.onOperationDone(total, success, failure, skipped)

                        context?.lbm?.unregisterReceiver(this)

                        Log.e(TAG, "Receive done, total $totalReceived broadcasts received")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(FileCopyService.ACTION_FILE_PROCESSED)
            addAction(FileCopyService.ACTION_OPERATION_DONE)
        }

        context.lbm.registerReceiver(receiver, filter)
        context.startForegroundService(intent)
    }

    /**
     * Delete selected files.
     */
    fun delete(
        context: Context,
        src: List<String>,
        callback: FileDeleteCallback? = null
    ) {
        val intent = Intent(context, FileDeleteService::class.java).apply {
            putStringArrayListExtra(FileDeleteService.EXTRA_SRC, src.toArrayList())
        }

        var totalReceived = 0 // How many broadcasts have been received, for debugging purpose
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    FileDeleteService.ACTION_FILE_PROCESSED -> {
                        ++totalReceived

                        val total = intent.getIntExtra(FileDeleteService.EXTRA_TOTAL_PROCESSED, -1)
                        val success = intent.getIntExtra(FileDeleteService.EXTRA_SUCCESS, -1)
                        val failure = intent.getIntExtra(FileDeleteService.EXTRA_FAILURE, -1)
                        callback?.onProgressUpdate(total, success, failure)
                    }

                    FileDeleteService.ACTION_OPERATION_DONE -> {
                        ++totalReceived

                        val total = intent.getIntExtra(FileDeleteService.EXTRA_TOTAL_PROCESSED, -1)
                        val success = intent.getIntExtra(FileDeleteService.EXTRA_SUCCESS, -1)
                        val failure = intent.getIntExtra(FileDeleteService.EXTRA_FAILURE, -1)
                        callback?.onOperationDone(total, success, failure)

                        context?.lbm?.unregisterReceiver(this)

                        Log.e(TAG, "Receive done, total $totalReceived broadcasts received")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(FileDeleteService.ACTION_FILE_PROCESSED)
            addAction(FileDeleteService.ACTION_OPERATION_DONE)
        }

        context.lbm.registerReceiver(receiver, filter)
        context.startForegroundService(intent)
    }
}
