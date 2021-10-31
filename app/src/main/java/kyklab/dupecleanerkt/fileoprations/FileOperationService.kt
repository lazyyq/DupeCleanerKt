package kyklab.dupecleanerkt.fileoprations

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class FileOperationService : LifecycleService() {
    companion object {
        private const val TAG = "FileOperationService"
    }

    protected abstract val notificationChannelId: String
    protected abstract val notificationChannelName: String

    protected abstract val foregroundNotificationBuilder: Notification.Builder
    protected abstract val notificationId: Int

    /**
     * Actual operation to do with the files. Will run on IO thread.
     */
    abstract suspend fun operate(intent: Intent)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            notificationChannelId, notificationChannelName, NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: throw RuntimeException("Failed to get NotificationManager")

        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Thread {
        //     doOperation()
        //     stopSelf()
        // }.start()
        // if (Looper.myLooper() == null) Looper.prepare()
        // super.onStartCommand(intent, flags, startId)

        if (intent == null) {
            Log.e(TAG, "FileOperationService cannot be called without intent")
            stopSelf()
            return START_NOT_STICKY
        }

        super.onStartCommand(intent, flags, startId)

        val notification = foregroundNotificationBuilder.build()
        startForeground(notificationId, notification)

        lifecycleScope.launch(Dispatchers.IO) {
            operate(intent)
            stopForeground(true)
            stopSelf()
        }
        return START_NOT_STICKY // Do not restart once service is killed
    }
}
