package kyklab.dupecleanerkt.ui.main

import android.app.Application
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class MainViewModel(application: Application): AndroidViewModel(application) {
    val isPermissionGranted = MutableLiveData(false)
    val isFolderPicked = MutableLiveData(false)
    val chosenDirectory = MutableLiveData("")
    val isDebug = MutableLiveData(false)
    val spinnerSelectedItem = MutableLiveData(1)
}