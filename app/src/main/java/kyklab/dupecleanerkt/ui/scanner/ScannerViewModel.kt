package kyklab.dupecleanerkt.ui.scanner

import android.app.Application
import android.widget.RadioGroup
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import kyklab.dupecleanerkt.R
import kyklab.dupecleanerkt.dupemanager.DupeManager

class ScannerViewModel(application: Application): AndroidViewModel(application) {
    val isScanDone: MutableLiveData<Boolean> = MutableLiveData(false)
    val totalScanned: MutableLiveData<Int> = MutableLiveData(0)
    val totalDupes: MutableLiveData<Int> = MutableLiveData(0)
    val totalChecked: MutableLiveData<Int> = MutableLiveData(0)
    val scanResultText: MediatorLiveData<String> = MediatorLiveData()

    init {
        scanResultText.addSource(totalScanned) {
            scanResultText.value = "$it scanned, ${totalDupes.value} duplicates found\n${totalChecked.value} checked"
        }
        scanResultText.addSource(totalDupes) {
            scanResultText.value = "${totalScanned.value} scanned, $it duplicates found\n${totalChecked.value} checked"
        }
        scanResultText.addSource(totalChecked) {
            scanResultText.value = "${totalScanned.value} scanned, ${totalDupes.value} duplicates found\n$it checked"
        }
    }

    // private val _sortMode = MutableLiveData(DupeManager.SORT_MODE_PATH)
    // val sortMode: LiveData<Int> = _sortMode

    // fun onSortOptionsCheckedChanged(group: RadioGroup, id: Int) {
    //     when (id) {
    //         R.id.rbSortByPath -> _sortMode.value = 0
    //         R.id.rbSortByLastModified -> _sortMode.value=1
    //     }
    // }
}