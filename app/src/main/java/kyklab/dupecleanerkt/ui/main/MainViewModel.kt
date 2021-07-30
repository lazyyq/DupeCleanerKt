package kyklab.dupecleanerkt.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import kyklab.dupecleanerkt.utils.Prefs

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val isPermissionGranted: MutableLiveData<Boolean> = MutableLiveData(false)
    val chosenDirectory: MutableLiveData<String> = MutableLiveData(
        Prefs.lastChosenDirPath ?: ""
    )
    val isFolderPicked: MutableLiveData<Boolean> = MutableLiveData(chosenDirectory.value?.isNotEmpty() ?: false)
    val isDebug: MutableLiveData<Boolean> = MutableLiveData(false)
    val spinnerSelectedItem: MutableLiveData<Int> = MutableLiveData(1)
    val isMediaScannerRunning: MutableLiveData<Boolean> = MutableLiveData(false)
    val runMediaScannerFirst: MutableLiveData<Boolean> = MutableLiveData(false)
    val isScanReady: MediatorLiveData<Boolean> = MediatorLiveData()

    init {
        isScanReady.addSource(isFolderPicked) {
            isScanReady.value = it && isMediaScannerRunning.value == false
        }
        isScanReady.addSource(isMediaScannerRunning) {
            isScanReady.value = !it && isFolderPicked.value == true
        }
    }
}