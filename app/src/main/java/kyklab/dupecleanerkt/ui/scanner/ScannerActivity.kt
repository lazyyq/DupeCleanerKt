package kyklab.dupecleanerkt.ui.scanner

import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anggrayudi.storage.callback.FileCallback
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.moveFileTo
import io.github.luizgrp.sectionedrecyclerviewadapter.Section
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kyklab.dupecleanerkt.R
import kyklab.dupecleanerkt.databinding.ActivityScannerBinding
import kyklab.dupecleanerkt.dupemanager.DupeManager
import kyklab.dupecleanerkt.utils.scanMediaFiles
import java.nio.file.Files
import java.nio.file.Paths

class ScannerActivity : AppCompatActivity() {
    companion object {
        const val INTENT_EXTRA_SCAN_PATH = "intent_extra_scan_path"
        const val INTENT_EXTRA_MATCH_MODE_INDEX = "intent_extra_match_mode_index"
    }

    private enum class CheckMode {
        ALL,
        ONLY_DUPLICATES,
        NONE,
    }

    private lateinit var viewModel: ScannerViewModel
    private lateinit var binding: ActivityScannerBinding

    private lateinit var dm: DupeManager
    private lateinit var adapter: SectionedRecyclerViewAdapter

    private lateinit var scanDirPath: String
    private lateinit var matchMode: DupeManager.MatchMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_scanner)
        viewModel = ViewModelProvider(this).get(ScannerViewModel::class.java)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        setupToolbar()

        scanDirPath = intent.getStringExtra(INTENT_EXTRA_SCAN_PATH) ?: run { finish(); return; }
        val matchModeIndex = intent.getIntExtra(INTENT_EXTRA_MATCH_MODE_INDEX, -1)
        matchMode = DupeManager.MatchMode.values()[matchModeIndex]

        setupListView()

        binding.checkBox.apply {
            setOnClickListener {
                if (isChecked && !isIndeterminate) {
                    check(CheckMode.ALL)
                } else if (isChecked && isIndeterminate) {
                    check(CheckMode.ONLY_DUPLICATES)
                } else if (!isChecked) {
                    check(CheckMode.NONE)
                }
            }
        }

        binding.ivSort.setOnClickListener {
            val items = arrayOf(
                "File path - Ascending",
                "File path - Descending",
                "Last modified date - Ascending",
                "Last modified date - Descending",
            )

            AlertDialog.Builder(this)
                .setTitle("Sort options")
                .setItems(items) { dialog, which ->
                    when (which) {
                        0 -> sort(DupeManager.SortMode.PATH_ASC)
                        1 -> sort(DupeManager.SortMode.PATH_DSC)
                        2 -> sort(DupeManager.SortMode.LAST_MODIFIED_DATE_ASC)
                        3 -> sort(DupeManager.SortMode.LAST_MODIFIED_DATE_DSC)
                        else -> throw RuntimeException("Unexpected sort option selected")
                    }
                }
                .show()
        }

//        binding.btnSort.setOnClickListener { sort() }
//        binding.btnSortReverse.setOnClickListener { sort(true) }

        binding.btnMove.setOnClickListener { moveChecked() }
        binding.btnDelete.setOnClickListener { deleteChecked() }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""
    }

    private fun setupListView() {
        adapter = SectionedRecyclerViewAdapter()
        binding.rv.layoutManager = LinearLayoutManager(this@ScannerActivity)
        binding.rv.adapter = adapter

        // Add sections
        Log.e("SCAN", "launching scanner with $scanDirPath")
        dm = DupeManager(this, lifecycleScope, scanDirPath, matchMode)
        dm.scan { duplicates, totalScanned, totalDuplicates ->
            Log.e("SCAN", "scanned $totalScanned, found $totalDuplicates")

            duplicates.forEach {
                val section = MusicSection(it) { section, itemAdapterPosition, newState ->
                    if (newState) viewModel.totalChecked.value = viewModel.totalChecked.value!! + 1
                    else viewModel.totalChecked.value = viewModel.totalChecked.value!! - 1
                }
                adapter.addSection(section)
            }

            runOnUiThread {
                adapter.notifyDataSetChanged()
                viewModel.apply {
                    this.totalScanned.value = totalScanned
                    totalDupes.value = totalDuplicates
                    totalChecked.value = getCheckedItemsCount()
                    isScanDone.value = true
                }
            }
        }
    }

    private fun sort(sortMode: DupeManager.SortMode) {
        viewModel.isScanDone.value = false
        dm.sort(sortMode) { duplicates ->
            runOnUiThread {
                adapter.notifyDataSetChanged()
                viewModel.isScanDone.value = true
                // Reset checked states
                binding.checkBox.setChecked(true, true)
                check(CheckMode.ONLY_DUPLICATES)
            }
        }
    }

    private fun moveChecked() {
        AlertDialog.Builder(this)
            .setMessage("Move the files?")
            .setPositiveButton("YES") { dialog, which -> folderPickerLauncher.launch(null) }
            .setNegativeButton("NO", null)
            .setCancelable(false)
            .show()
    }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult

            val targetDir =
                DocumentFileCompat.fromUri(this, uri) ?: run {
                    Log.e("Launcher callback", "uri is null")
                    return@registerForActivityResult
                }
            val targetDirPath = targetDir.getAbsolutePath(this)
            _moveChecked(targetDirPath)
        }

    private fun _moveChecked(targetDirPath: String) {
        // Show progress dialog while moving files
        val moveProgressDialog = ProgressDialog(this).apply {
            setTitle("Moving files")
            setMessage("Please keep the app open while during operation")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            max = viewModel.totalChecked.value!!
            show()
        }

        var success = 0
        var failure = 0
        var skipped = 0

        val fileCallback = object : FileCallback() {
            override fun onFailed(errorCode: ErrorCode) {
                ++failure
                Log.e("fileCallback", "Failed")
                ++moveProgressDialog.progress
            }

            override fun onCompleted(result: Any) {
                ++success
                ++moveProgressDialog.progress
            }

            override fun onConflict(destinationFile: DocumentFile, action: FileConflictAction) {
                action.confirmResolution(ConflictResolution.SKIP)
                Log.e("fileCallback", "Skipped")
                ++skipped
                ++moveProgressDialog.progress
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val context = this@ScannerActivity
            adapter.forEachSection { section ->
                if (section is MusicSection) {
                    section.list.asSequence().withIndex()
                        .filter { section.checkedIndexes[it.index] }
                        .forEach {
                            try {
                                val file = DocumentFileCompat.fromFullPath(
                                    context,
                                    it.value.path
                                )
                                val filename = file!!.name!!
//                                    val parentDir = file!!.parentFile!!.getAbsolutePath(context)
                                val parentDir =
                                    with(it.value.path) { substring(0 until length - filename.length - 1) }
                                val subDir =
                                    parentDir.substring(startIndex = scanDirPath.length /*+ 1*/)

//                                    val appendix1 = targetDir.getAbsolutePath(context)
//                                    val appendix2 = subDir!!.getAbsolutePath(context)
//                                    Log.e("APPENDIX", "1:$appendix1, 2:$appendix2")
                                val moveDirPath = "$targetDirPath/$subDir"
                                if (!DocumentFileCompat.doesExist(
                                        context,
                                        moveDirPath
                                    )
                                ) DocumentFileCompat.mkdirs(context, moveDirPath)

                                val moveDir = DocumentFileCompat.fromFullPath(
                                    context,
                                    moveDirPath
                                )!!
                                /*if (!moveDir.exists()) {
                                val subDirs = subDir.split("/")
                                var documentFile = targetDir
                                subDirs.forEach { dirname ->
                                    documentFile = documentFile.createDirectory(dirname)!!
                                }
                            }*/

                                file.moveFileTo(
                                    context = context,
                                    targetFolder = moveDir,
                                    callback = fileCallback
                                )
                            } catch (e: NullPointerException) {
                                Log.e("Mover", "NPE")
                                e.printStackTrace()
                                ++failure
                            }
                        }
                }
            } // forEachSection done

            launch(Dispatchers.Main) {
                moveProgressDialog.dismiss()
            }

            // Run media scanner to update info for moved files
            lateinit var scanProgressDialog: ProgressDialog
            launch(Dispatchers.Main) {
                scanProgressDialog = ProgressDialog(this@ScannerActivity).apply {
                    setTitle("Updating database")
                    setMessage("Please keep the app open while during operation")
                    setProgressStyle(ProgressDialog.STYLE_SPINNER)
                    setCancelable(false)
                    show()
                }
            }

            scanMediaFiles(arrayOf(scanDirPath, targetDirPath)) { path, uri ->
                // launch(Dispatchers.Main) { // This causes callback to hang
                scanProgressDialog.dismiss()

                AlertDialog.Builder(context)
                    .setTitle("Move complete!")
                    .setMessage(
                        "Success: $success\nFailure: $failure\nSkipped: $skipped"
                    )
                    .setPositiveButton("OK") { dialog, which ->
                        this@ScannerActivity.finish()
                    }
                    .setCancelable(false)
                    .show()
                // } // launch(Dispatchers.Main)
            }
        }
    }

    private fun deleteChecked() {
        AlertDialog.Builder(this)
            .setTitle("Are you sure?")
            .setPositiveButton("YES") { dialog, which -> _deleteChecked() }
            .setNegativeButton("NO", null)
            .setCancelable(false)
            .show()
    }

    private fun _deleteChecked() {
        val deleteProgressDialog = ProgressDialog(this).apply {
            setTitle("Deleting duplicates")
            setMessage("Please keep the app open while during operation")
            setCancelable(false)
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = viewModel.totalChecked.value!!
            show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var success = 0
            var failure = 0
            adapter.forEachSection { section ->
                if (section is MusicSection) {
                    section.list.asSequence().withIndex()
                        .filter { section.checkedIndexes[it.index] }
                        .forEach {
                            try {
                                if (Files.deleteIfExists(Paths.get(it.value.path))) {
                                    ++success
                                } else {
                                    ++failure
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                ++failure
                            } finally {
                                ++deleteProgressDialog.progress
                            }
                        } // for each items in section
                }
            } // forEachSection

            lateinit var scanProgressDialog: ProgressDialog

            launch(Dispatchers.Main) {
                deleteProgressDialog.dismiss()

                scanProgressDialog = ProgressDialog(this@ScannerActivity).apply {
                    setTitle("Updating database")
                    setMessage("Please keep the app open while during operation")
                    setProgressStyle(ProgressDialog.STYLE_SPINNER)
                    setCancelable(false)
                    show()
                }
            }

            scanMediaFiles(scanDirPath) { path, uri ->
                scanProgressDialog.dismiss()

                AlertDialog.Builder(this@ScannerActivity)
                    .setTitle("Delete complete!")
                    .setMessage("Success: $success\nFailure: $failure")
                    .setPositiveButton("OK") { dialog, which -> this@ScannerActivity.finish() }
                    .setCancelable(false)
                    .show()
            }
        } // Dispatchers.IO
    }

    private fun getCheckedItemsCount(): Int {
        var result = 0
        adapter.forEachSection { section ->
            if (section is MusicSection) {
                result += section.checkedIndexes.count { it }
            }
        }
        return result
    }

    private fun check(mode: CheckMode) {
        when (mode) {
            CheckMode.ALL ->
                adapter.forEachSection { section ->
                    (section as? MusicSection)?.checkedIndexes?.fill(true)
                }

            CheckMode.ONLY_DUPLICATES ->
                adapter.forEachSection { section ->
                    (section as? MusicSection)?.checkedIndexes?.let {
                        it[0] = false
                        it.fill(true, fromIndex = 1)
                    }
                }

            CheckMode.NONE ->
                adapter.forEachSection { section ->
                    (section as? MusicSection)?.checkedIndexes?.fill(false)
                }
        }

        viewModel.totalChecked.value = getCheckedItemsCount()
        adapter.notifyDataSetChanged()
    }

    private fun SectionedRecyclerViewAdapter.forEachSection(block: (section: Section) -> Unit) {
        for (i in 0 until sectionCount) {
            block(getSection(i))
        }
    }
}