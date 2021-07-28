package kyklab.dupecleanerkt.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.callback.FolderPickerCallback
import com.anggrayudi.storage.callback.StorageAccessCallback
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.StorageType
import com.anggrayudi.storage.file.getAbsolutePath
import kyklab.dupecleanerkt.R
import kyklab.dupecleanerkt.databinding.ActivityMainBinding
import kyklab.dupecleanerkt.ui.scanner.ScannerActivity
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        val REQUEST_CODE_STORAGE_ACCESS = 100
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding

    private lateinit var permissions: Array<String>

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            var result = true
            map.entries.forEach { if (!it.value) result = false }
            viewModel.isPermissionGranted.value = result
        }


    private val simpleStorage = SimpleStorage(this)

    private val storageAccessCallback = object : StorageAccessCallback {
        override fun onRootPathNotSelected(
            requestCode: Int,
            rootPath: String,
            uri: Uri,
            selectedStorageType: StorageType,
            expectedStorageType: StorageType
        ) {
            Log.e(
                "StorageAccessCallback onRootPathNotSelected",
                "requestCode: $requestCode\nrootPath: $rootPath\nuri: $uri\nselectedStorageType: $selectedStorageType\nexpectedStorageType: $expectedStorageType"
            )
        }

        override fun onRootPathPermissionGranted(requestCode: Int, root: DocumentFile) {
            Log.e(
                "StorageAccessCallback onRootPathPermissionGranted",
                "requestCode: $requestCode\nroot: $root"
            )
        }

        override fun onStoragePermissionDenied(requestCode: Int) {
            Log.e("StorageAccessCallback onStoragePermissionDenied", "requestCode: $requestCode")
        }
    }

    private val folderPickerCallback = object : FolderPickerCallback {
        override fun onActivityHandlerNotFound(requestCode: Int, intent: Intent) {
            Log.e(
                "FolderPickerCallback onActivityHandlerNotFound",
                "requestCode: $requestCode\nintent: $intent"
            )
        }

        override fun onCanceledByUser(requestCode: Int) {
            Log.e("FolderPickerCallback onCanceledByUser", "requestCode: $requestCode")
        }

        override fun onFolderSelected(requestCode: Int, folder: DocumentFile) {
            Log.e(
                "FolderPickerCallback onFolderSelected",
                "requestCode: $requestCode\nfolder: $folder"
            )

            val path = folder.getAbsolutePath(this@MainActivity)
            viewModel.chosenDirectory.value = path
        }

        override fun onStorageAccessDenied(
            requestCode: Int,
            folder: DocumentFile?,
            storageType: StorageType
        ) {
            Log.e(
                "FolderPickerCallback onStorageAccessDenied",
                "requestCode: $requestCode\nfolder: $folder\nstorageType: $storageType"
            )
        }

        override fun onStoragePermissionDenied(requestCode: Int) {
            Log.e("FolderPickerCallback onStoragePermissionDenied", "requestCode: $requestCode")
        }
    }

    /**
     * Old code for requesting directory permission
     */
    /*
    private val folderPickerIntentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent -> handleDirectoryUri(intent) }
            }
        }
    */
    private var path: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        if (isAtLeastR) {
            checkPermissionsR()
        } else {
            checkPermissions()
        }

        binding.btnRequestPermission.setOnClickListener { requestPermissions() }
        binding.btnPickFolder.setOnClickListener { openFolderPicker() }
        binding.btnTest.setOnClickListener { test() }
        binding.btnCreate.setOnClickListener { create() }
        binding.btnRemove.setOnClickListener { remove() }
        binding.btnGo.setOnClickListener { go() }

        setupSpinner()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkPermissionsR() {
        viewModel.isPermissionGranted.value = Environment.isExternalStorageManager()
    }

    private fun checkPermissions() {
        permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        var granted = true
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it) ==
                PackageManager.PERMISSION_DENIED
            ) {
                granted = false
            }
        }
        viewModel.isPermissionGranted.value = granted
    }

    private val permissionActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isAtLeastR) {
                checkPermissionsR()
            }
        }

    private fun requestPermissions() {
        if (isAtLeastR) {
            permissionActivityLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
//            startActivityForResult(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), 1000)
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it ?: return@registerForActivityResult

            path = DocumentFileCompat.fromUri(this, it)?.getAbsolutePath(this) ?: return@registerForActivityResult

            viewModel.isFolderPicked.value=true
            viewModel.chosenDirectory.value=path
        }

    private fun openFolderPicker() {
//        simpleStorage.storageAccessCallback = storageAccessCallback
//        simpleStorage.requestStorageAccess()

//        simpleStorage.folderPickerCallback = folderPickerCallback
//        simpleStorage.openFolderPicker()

//        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
//        startActivityForResult(intent, 1)

        folderPickerLauncher.launch(null)
    }

    private fun test() {
        val path = "/storage/emulated/0/Android/data/.nomedia"
        val file = DocumentFileCompat.fromFullPath(this, path)
        file?.name?.let { Log.e("TEST", it) }
    }

    private fun create() {
        val path = "/storage/emulated/0/test"
        val file = DocumentFileCompat.fromFullPath(this, path)
        if (file == null) {
            Log.e("create()", "file is null")
        }
        if (file == null || !file.exists()) {
            val result = File(path).createNewFile()
            Log.e("Create new file", "result: $result")
        }
    }

    private fun remove() {
        val path = "/storage/emulated/0/test"
        val file = DocumentFileCompat.fromFullPath(this, path)
        val result = file?.delete()
        Log.e("remove", "result: $result")
    }

    private fun go() {
        if (path == null) {
            Toast.makeText(this, "Path not selected", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(this, ScannerActivity::class.java)
            intent.putExtra(ScannerActivity.INTENT_EXTRA_SCAN_PATH, path)
            intent.putExtra(ScannerActivity.INTENT_EXTRA_MATCH_MODE, viewModel.spinnerSelectedItem.value)
            startActivity(intent)
        }
    }

    private fun setupSpinner() {
        viewModel.spinnerSelectedItem.observe(this) {
            Log.e("Observer","Spinner value updated: $it")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Mandatory for Activity, but not for Fragment
//        Log.e("RESULT", "$requestCode, $resultCode, $data")
        simpleStorage.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        simpleStorage.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        simpleStorage.onRestoreInstanceState(savedInstanceState)
    }

    /**
     * Old code for requesting directory permission
     */
    /*
    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        folderPickerIntentLauncher.launch(intent)
    }

    private fun handleDirectoryUri(intent: Intent) {
        directoryUri = intent.data
        Log.e("RESULT", directoryUri.toString())
        viewModel.isFolderPicked.value = true
        viewModel.chosenDirectory
    }
    */

    private inline val isAtLeastR
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}