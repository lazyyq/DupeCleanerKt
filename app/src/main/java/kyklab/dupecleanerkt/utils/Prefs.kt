package kyklab.dupecleanerkt.utils

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kyklab.dupecleanerkt.App

object Prefs {
    private val pref = PreferenceManager.getDefaultSharedPreferences(App.context)
    private val editor = pref.edit()

    init {
        editor.apply()
    }

    var lastKnownAppVersion: Int
        get() = pref.getInt(Key.LAST_KNOWN_APP_VERSION, 1)
        set(value) = editor.putInt(Key.LAST_KNOWN_APP_VERSION, value).apply()


    fun registerPrefChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        pref.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPrefChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        pref.unregisterOnSharedPreferenceChangeListener(listener)
    }

    object Key {
        const val LAST_KNOWN_APP_VERSION = "last_known_app_version"
    }
}