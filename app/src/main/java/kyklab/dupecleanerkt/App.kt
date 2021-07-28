package kyklab.dupecleanerkt

import android.app.Application
import android.content.Context
import kyklab.dupecleanerkt.utils.Prefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class App : Application() {
    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()

        application = this
        if (BuildConfig.DEBUG) {
            defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler())
        }

        Prefs.lastKnownAppVersion = BuildConfig.VERSION_CODE
    }

    companion object {
        private lateinit var application: Application
        val context: Context
            get() = application.applicationContext
    }

    private inner class ExceptionHandler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            val date = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val output: String =
                getExternalFilesDir(null).toString() + File.separator + date + ".txt"
            File(output).printWriter().use { e.printStackTrace(it) }

            defaultUncaughtExceptionHandler?.uncaughtException(t, e)
        }
    }
}

