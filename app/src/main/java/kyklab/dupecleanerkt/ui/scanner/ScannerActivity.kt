package kyklab.dupecleanerkt.ui.scanner

import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.getAbsolutePath
import com.leinardi.android.speeddial.SpeedDialActionItem
import io.github.luizgrp.sectionedrecyclerviewadapter.Section
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kyklab.dupecleanerkt.R
import kyklab.dupecleanerkt.databinding.ActivityScannerBinding
import kyklab.dupecleanerkt.dupemanager.DupeManager
import kyklab.dupecleanerkt.fileoprations.FileOperations
import kyklab.dupecleanerkt.utils.FastScrollableSectionedRecyclerViewAdapter
import kyklab.dupecleanerkt.utils.scanMediaFiles
import java.util.*

class ScannerActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "ScannerActivity"

        const val INTENT_EXTRA_SCAN_PATH = "intent_extra_scan_path"
        const val INTENT_EXTRA_MATCH_MODE_INDEX = "intent_extra_match_mode_index"
        const val INTENT_EXTRA_RUN_MEDIA_SCANNER = "intent_extra_run_media_scanner"
    }

    private enum class CheckMode {
        ALL,
        ONLY_DUPLICATES,
        NONE,
    }

    /**
     * Options for sorting sections
     */
    enum class SortSectionsOptions {
        TITLE,
        ARTIST,
    }

    /**
     * Options for sorting items inside each section
     */
    enum class SortItemsOptions {
        FILE_PATH_ASC,
        FILE_PATH_DSC,
        LAST_MODIFIED_DATE_ASC,
        LAST_MODIFIED_DATE_DSC,
    }

    private lateinit var viewModel: ScannerViewModel
    private lateinit var binding: ActivityScannerBinding

    private lateinit var dm: DupeManager
    private lateinit var adapter: FastScrollableSectionedRecyclerViewAdapter

    private lateinit var scanDirPath: String
    private lateinit var targetDirPath: String
    private lateinit var matchMode: DupeManager.MatchMode
    private var runMediaScannerFirst = false

    // Whether to override if a file with the same name exists when moving duplicates
    private var overrideOnMove = false

    private lateinit var sortOptionsDialogListener: SortOptionsDialogFragment.SortOptionsDialogListener

    private var selectedSortSectionsOption: SortSectionsOptions? = null
    private var selectedSortItemsOption: SortItemsOptions? = null

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
        runMediaScannerFirst = intent.getBooleanExtra(INTENT_EXTRA_RUN_MEDIA_SCANNER, false)

        setupListView()
        setupFab()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""

        // Toggle checkbox
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

        // Set sort options dialog callback listener
        sortOptionsDialogListener = object : SortOptionsDialogFragment.SortOptionsDialogListener {
            override fun onDialogPositiveClick(
                dialog: DialogFragment,
                sortSectionsOption: SortSectionsOptions?,
                sortItemsOption: SortItemsOptions?
            ) {
                selectedSortSectionsOption = sortSectionsOption
                selectedSortItemsOption = sortItemsOption
                sort(sortSectionsOption, sortItemsOption)
            }

            override fun onDialogNegativeClick(
                dialog: DialogFragment,
                sortSectionsOption: SortSectionsOptions?,
                sortItemsOption: SortItemsOptions?
            ) {

            }
        }

        // Sort button
        binding.ivSort.setOnClickListener {
            SortOptionsDialogFragment(
                sortOptionsDialogListener,
                selectedSortSectionsOption,
                selectedSortItemsOption
            ).show(
                supportFragmentManager,
                "sortOptions"
            )
        }
    }

    private fun setupListView() {
        adapter = FastScrollableSectionedRecyclerViewAdapter()
        binding.rv.layoutManager = LinearLayoutManager(this@ScannerActivity)
        binding.rv.adapter = adapter

        // Add sections
        dm = DupeManager(this, lifecycleScope, scanDirPath, matchMode)
        dm.scan(runMediaScannerFirst) { duplicates, totalScanned, totalDuplicates ->
            Log.e(TAG, "scanned $totalScanned, found $totalDuplicates")

            addSections()

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

    private fun setupFab() {
        binding.fab.addActionItem(
            SpeedDialActionItem.Builder(R.id.fab_delete, R.drawable.ic_delete_forever_outlined)
                .setLabel("Delete selected")
                .setFabBackgroundColor(resources.getColor(R.color.white, theme))
                .create()
        )
        binding.fab.addActionItem(
            SpeedDialActionItem.Builder(R.id.fab_move, R.drawable.ic_drive_file_move_outlined)
                .setLabel("Move selected")
                .setFabBackgroundColor(resources.getColor(R.color.white, theme))
                .create()
        )
        binding.fab.setOnActionSelectedListener { actionItem: SpeedDialActionItem? ->
            when (actionItem?.id) {
                R.id.fab_delete -> {
                    deleteChecked()
                }
                R.id.fab_move -> {
                    moveChecked()
                }
            }
            false
        }
    }

    private fun addSections() {
        // TODO: Compare performance with previous version
        dm.dupeList.asSequence().map {
            MusicSection(it) { section, itemAdapterPosition, newState ->
                if (newState) viewModel.totalChecked.value = viewModel.totalChecked.value!! + 1
                else viewModel.totalChecked.value = viewModel.totalChecked.value!! - 1
            }
        }.forEach { adapter.addSection(it) }
    }

    private fun sort(sortSectionsOption: SortSectionsOptions?, sortItemsOption: SortItemsOptions?) {
        viewModel.isScanDone.value = false

        lifecycleScope.launch(Dispatchers.Default) {
            // Sort items inside sections first
            when (sortItemsOption) {
                SortItemsOptions.FILE_PATH_ASC ->
                    dm.dupeList.forEach { list -> list.sortBy { music -> music.path } }
                SortItemsOptions.FILE_PATH_DSC ->
                    dm.dupeList.forEach { list -> list.sortByDescending { music -> music.path } }
                SortItemsOptions.LAST_MODIFIED_DATE_ASC ->
                    dm.dupeList.forEach { list -> list.sortBy { music -> music.dateModified } }
                SortItemsOptions.LAST_MODIFIED_DATE_DSC ->
                    dm.dupeList.forEach { list -> list.sortByDescending { music -> music.dateModified } }
            }

            // Then sort sections
            when (sortSectionsOption) {
                SortSectionsOptions.TITLE ->
                    dm.dupeList.sortBy { it.first().title }
                SortSectionsOptions.ARTIST ->
                    dm.dupeList.sortBy { it.first().artist }
            }

            adapter.removeAllSections()
            addSections()

            // Update UI
            runOnUiThread {
                adapter.notifyDataSetChanged()
                viewModel.isScanDone.value = true
                // Reset checked states
                binding.checkBox.setChecked(true, true)
                check(CheckMode.ONLY_DUPLICATES)
                val dd = dm.dupeList
            }
        }
    }

    private fun moveChecked() {
        AlertDialog.Builder(this)
            .setTitle("Move the files? Directory structure will be kept.")
            .setMultiChoiceItems(
                arrayOf("Override if a file with the same name exists"),
                booleanArrayOf(false)
            ) { dialog, which, isChecked -> overrideOnMove = isChecked }
            .setPositiveButton("YES") { dialog, which ->
                moveFolderPickerLauncher.launch(null)
            }
            .setNegativeButton("NO", null)
            .setCancelable(false)
            .show()
    }

    private val moveFolderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult

            val targetDir =
                DocumentFileCompat.fromUri(this, uri) ?: run {
                    return@registerForActivityResult
                }
            targetDirPath = targetDir.getAbsolutePath(this)
            moveCheckedContd()
        }

    private fun moveCheckedContd() {
        // Show progress dialog while moving files
        val moveProgressDialog = ProgressDialog(this).apply {
            setTitle("Moving files")
            setMessage("Please keep the app open during the operation.")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            max = viewModel.totalChecked.value!!
            show()
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val list = getCheckedMusicFilePaths()
            val callback = object : FileOperations.FileCopyCallback {
                override fun onProgressUpdate(
                    total: Int, success: Int, failure: Int, skipped: Int
                ) {
                    moveProgressDialog.progress = total
                }

                override fun onOperationDone(
                    total: Int, success: Int, failure: Int, skipped: Int
                ) {
                    moveProgressDialog.dismiss()
                    finishMove(success, failure, skipped)
                }
            }
            FileOperations.copy(
                this@ScannerActivity, list, targetDirPath,
                scanDirPath, move = true, override = overrideOnMove, callback
            )
        }
    }

    private fun finishMove(success: Int, failure: Int, skipped: Int) {
        // Run media scanner to update info for moved files
        // launch(Dispatchers.Main) {
        val scanProgressDialog: ProgressDialog = ProgressDialog(this@ScannerActivity).apply {
            setTitle("Updating database")
            setMessage("Please keep the app open during the operation")
            setProgressStyle(ProgressDialog.STYLE_SPINNER)
            setCancelable(false)
            show()
        }
        // }
        scanMediaFiles(arrayOf(scanDirPath, targetDirPath)) { path, uri ->
            // launch(Dispatchers.Main) { // This causes callback to hang
            scanProgressDialog.dismiss()

            AlertDialog.Builder(this@ScannerActivity)
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

    private fun deleteChecked() {
        AlertDialog.Builder(this)
            .setTitle("Are you sure you want to delete?")
            .setMessage("Warning: This task cannot be cancelled or be reverted")
            .setPositiveButton("YES") { dialog, which -> deleteCheckedContd() }
            .setNegativeButton("NO", null)
            .setCancelable(false)
            .show()
    }

    private fun deleteCheckedContd() {
        val deleteProgressDialog = ProgressDialog(this).apply {
            setTitle("Deleting files")
            setMessage("Please keep the app open during the operation.")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            max = viewModel.totalChecked.value!!
            show()
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val list = getCheckedMusicFilePaths()
            val callback = object : FileOperations.FileDeleteCallback {
                override fun onProgressUpdate(total: Int, success: Int, failure: Int) {
                    deleteProgressDialog.progress = total
                }

                override fun onOperationDone(total: Int, success: Int, failure: Int) {
                    deleteProgressDialog.dismiss()
                    finishDelete(success, failure)
                }
            }
            FileOperations.delete(this@ScannerActivity, list, callback)
        }
    }

    private fun finishDelete(success: Int, failure: Int) {
        // Run media scanner to update info for moved files
        val scanProgressDialog: ProgressDialog = ProgressDialog(this@ScannerActivity).apply {
            setTitle("Updating database")
            setMessage("Please keep the app open during the operation")
            setProgressStyle(ProgressDialog.STYLE_SPINNER)
            setCancelable(false)
            show()
        }
        scanMediaFiles(arrayOf(scanDirPath)) { path, uri ->
            scanProgressDialog.dismiss()

            AlertDialog.Builder(this@ScannerActivity)
                .setTitle("Delete complete!")
                .setMessage(
                    "Success: $success\nFailure: $failure"
                )
                .setPositiveButton("OK") { dialog, which ->
                    this@ScannerActivity.finish()
                }
                .setCancelable(false)
                .show()
        }
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

    private fun getCheckedMusicFilePaths(): ArrayList<String> {
        val list = ArrayList<String>(viewModel.totalChecked.value ?: 10)
        adapter.forEachSection { section ->
            (section as MusicSection).list.asSequence().withIndex()
                .filter { section.checkedIndexes[it.index] }.map { it.value.path }.let {
                    list.addAll(it)
                }
        }
        return list
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
        adapter.notifyItemRangeChanged(
            0, adapter.itemCount,
            MusicSection.PAYLOAD_TRIGGER_CHECKBOX_STATE_UPDATE
        )
    }

    private fun SectionedRecyclerViewAdapter.forEachSection(block: (section: Section) -> Unit) {
        for (i in 0 until sectionCount) {
            block(getSection(i))
        }
    }
}