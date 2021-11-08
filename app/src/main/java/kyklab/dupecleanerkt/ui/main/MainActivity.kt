package kyklab.dupecleanerkt.ui.main

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.getAbsolutePath
import kyklab.dupecleanerkt.BuildConfig
import kyklab.dupecleanerkt.R
import kyklab.dupecleanerkt.databinding.ActivityMainBinding
import kyklab.dupecleanerkt.ui.scanner.ScannerActivity
import kyklab.dupecleanerkt.utils.Prefs
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding

    private val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            var result = true
            map.entries.forEach { if (!it.value) result = false }
            viewModel.isPermissionGranted.value = result
        }

    private val simpleStorage = SimpleStorage(this)

    /*
    /**
     * Old code for requesting directory permission
     */
    private val folderPickerIntentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent -> handleDirectoryUri(intent) }
            }
        }
    */
    private var path = Prefs.lastChosenDirPath

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen().setOnExitAnimationListener { splashScreenViewProvider ->
            ObjectAnimator.ofFloat(
                splashScreenViewProvider.view, View.ALPHA, 1f, 0f
            ).apply {
                duration = 500L
                doOnEnd { splashScreenViewProvider.remove() }
                start()
            }
        }

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        if (isAtLeastR) {
            checkPermissionsR()
        } else {
            checkPermissions()
        }

        binding.btnGrantPermissions.setOnClickListener { requestPermissions() }
        binding.btnChooseDirectory.setOnClickListener { openFolderPicker() }
        binding.btnGo.setOnClickListener { go() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkPermissionsR() {
        viewModel.isPermissionGranted.value = Environment.isExternalStorageManager()
    }

    private fun checkPermissions() {
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
            permissionActivityLauncher.launch(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                )
            )
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it ?: return@registerForActivityResult

            path = DocumentFileCompat.fromUri(this, it)?.getAbsolutePath(this)
                ?: return@registerForActivityResult

            viewModel.isFolderPicked.value = true
            viewModel.chosenDirectory.value = path

            Prefs.lastChosenDirPath = path
        }

    private fun openFolderPicker() {
        folderPickerLauncher.launch(null)
    }

    private fun go() {
        if (path == null) {
            Toast.makeText(this, "Path not selected", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(this, ScannerActivity::class.java).apply {
                putExtra(ScannerActivity.INTENT_EXTRA_SCAN_PATH, path)
                putExtra(
                    ScannerActivity.INTENT_EXTRA_MATCH_MODE_INDEX,
                    viewModel.selectedMatchMode.value
                )
                putExtra(
                    ScannerActivity.INTENT_EXTRA_RUN_MEDIA_SCANNER,
                    viewModel.runMediaScannerFirst.value
                )
            }
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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

    private inline val isAtLeastR
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}