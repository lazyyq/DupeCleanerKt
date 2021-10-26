package kyklab.dupecleanerkt.ui.scanner

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
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
import kyklab.dupecleanerkt.utils.FastScrollableSectionedRecyclerViewAdapter
import kyklab.dupecleanerkt.utils.scanMediaFiles
import java.nio.file.Files
import java.nio.file.Paths

class ScannerActivity : AppCompatActivity() {
    companion object {
        const val INTENT_EXTRA_SCAN_PATH = "intent_extra_scan_path"
        const val INTENT_EXTRA_MATCH_MODE_INDEX = "intent_extra_match_mode_index"
        const val INTENT_EXTRA_RUN_MEDIA_SCANNER = "intent_extra_run_media_scanner"
    }

    private enum class CheckMode {
        ALL, ONLY_DUPLICATES, NONE,
    }

    /**
     * Options for sorting sections
     */
    enum class SortSectionsOptions {
        TITLE, ARTIST, LAST_MODIFIED_DATE,
    }

    /**
     * Options for sorting items inside each section
     */
    enum class SortItemsOptions {
        FILE_PATH, LAST_MODIFIED_DATE,
    }

    private lateinit var viewModel: ScannerViewModel
    private lateinit var binding: ActivityScannerBinding

    private lateinit var dm: DupeManager
    private lateinit var adapter: FastScrollableSectionedRecyclerViewAdapter

    private lateinit var scanDirPath: String
    private lateinit var matchMode: DupeManager.MatchMode
    private var runMediaScannerFirst = false

    // Result listener for sort options dialog
    private lateinit var sortOptionsDialogListener: SortOptionsDialogFragment.SortOptionsDialogListener

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

        binding.btnMove.setOnClickListener { moveChecked() }
        binding.btnDelete.setOnClickListener { deleteChecked() }
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
                sortSectionsOption: SortSectionsOptions,
                sortItemsOption: SortItemsOptions
            ) {
                sort(sortSectionsOption, sortItemsOption)
            }

            override fun onDialogNegativeClick(
                dialog: DialogFragment,
                sortSectionsOption: SortSectionsOptions,
                sortItemsOption: SortItemsOptions
            ) {

            }
        }

        // Sort button
        binding.ivSort.setOnClickListener {
            SortOptionsDialogFragment(sortOptionsDialogListener).show(
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
        Log.e("SCAN", "launching scanner with $scanDirPath")
        dm = DupeManager(this, lifecycleScope, scanDirPath, matchMode)
        dm.scan(runMediaScannerFirst) { duplicates, totalScanned, totalDuplicates ->
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
                Log.e("SCAN", "total sections ${adapter.sectionCount}")
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun sort(sortSectionsOption: SortSectionsOptions, sortItemsOption: SortItemsOptions) {
        viewModel.isScanDone.value = false

        lifecycleScope.launch(Dispatchers.Default) {
            // TODO: Add option to display in descending order?
            val dd = dm.dupeList
            // Sort items first
            when (sortItemsOption) {
                SortItemsOptions.FILE_PATH -> dm.dupeList.forEach { list -> list.sortBy { music -> music.path } }
                SortItemsOptions.LAST_MODIFIED_DATE -> dm.dupeList.forEach { list -> list.sortBy { music -> music.dateModified } }
            }
            // Sort sections
            /*when (sortSectionsOption) {
                SortSectionsOptions.TITLE -> dm.dupeList.sortBy { it.first().title }
                SortSectionsOptions.ARTIST -> dm.dupeList.sortBy { it.first().artist }
                SortSectionsOptions.LAST_MODIFIED_DATE -> dm.dupeList.sortBy { it.first().dateModified }
            }*/
            // Update UI
            runOnUiThread {
//                adapter.removeAllSections()
                adapter.notifyDataSetChanged()

                val ds = dm.dupeList

/*
                dm.dupeList.forEach {
                    val section = MusicSection(it) { section, itemAdapterPosition, newState ->
                        if (newState) viewModel.totalChecked.value = viewModel.totalChecked.value!! + 1
                        else viewModel.totalChecked.value = viewModel.totalChecked.value!! - 1
                    }
                    adapter.addSection(section)
                }
 */

                viewModel.isScanDone.value = true
                Log.e("tag", "called")
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
            moveCheckedInternal(targetDirPath)
        }

    private fun moveCheckedInternal(targetDirPath: String) {
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
            .setPositiveButton("YES") { dialog, which -> deleteCheckedInternal() }
            .setNegativeButton("NO", null)
            .setCancelable(false)
            .show()
    }

    private fun deleteCheckedInternal() {
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

    class SortOptionsDialogFragment(private val listener: SortOptionsDialogListener) :
        DialogFragment() {
        private var width = 0f

        interface SortOptionsDialogListener {
            fun onDialogPositiveClick(
                dialog: DialogFragment,
                sortSectionsOption: SortSectionsOptions,
                sortItemsOption: SortItemsOptions
            )

            fun onDialogNegativeClick(
                dialog: DialogFragment,
                sortSectionsOption: SortSectionsOptions,
                sortItemsOption: SortItemsOptions
            )
        }

        override fun onStart() {
            super.onStart()
            // Set dialog width to almost wrap_content. Without this it almost fills the whole screen.
            dialog?.window?.setLayout(width.toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        @SuppressLint("InflateParams")
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return activity?.let {
                val builder = android.app.AlertDialog.Builder(it)
                val inflater = requireActivity().layoutInflater
                val view = inflater.inflate(R.layout.dialog_music_sort_options, null)

                // Calculate the width of layout so we can adjust the dialog width accordingly.
                view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                width = view.measuredWidth * 1.2f

                val sortSectionsOption = when {
                    view.findViewById<RadioButton>(R.id.rbTitle).isChecked -> SortSectionsOptions.TITLE
                    view.findViewById<RadioButton>(R.id.rbArtist).isChecked -> SortSectionsOptions.ARTIST
                    view.findViewById<RadioButton>(R.id.rbSectionLastModifiedDate).isChecked -> SortSectionsOptions.LAST_MODIFIED_DATE
                    else -> throw Exception("At least one section sort option must be checked")
                }

                val sortItemsOption = when {
                    view.findViewById<RadioButton>(R.id.rbFilePath).isChecked -> SortItemsOptions.FILE_PATH
                    view.findViewById<RadioButton>(R.id.rbItemLastModifiedDate).isChecked -> SortItemsOptions.LAST_MODIFIED_DATE
                    else -> throw Exception("At least one item sort option must be checked")
                }

                builder.setView(view)
                    .setPositiveButton(android.R.string.ok) { dialogInterface, i ->
                        listener.onDialogPositiveClick(this, sortSectionsOption, sortItemsOption)
                    }
                    .setNegativeButton(android.R.string.cancel) { dialogInterface, i ->
                        listener.onDialogNegativeClick(this, sortSectionsOption, sortItemsOption)
                    }
                builder.create()
            } ?: throw IllegalStateException("Activity cannot be null")
        }
    }
}