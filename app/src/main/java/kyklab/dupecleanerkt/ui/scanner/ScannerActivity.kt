package kyklab.dupecleanerkt.ui.scanner

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
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kyklab.dupecleanerkt.R
import kyklab.dupecleanerkt.databinding.ActivityScannerBinding
import kyklab.dupecleanerkt.dupemanager.DupeManager

class ScannerActivity : AppCompatActivity() {
    companion object {
        const val INTENT_EXTRA_SCAN_PATH = "intent_extra_scan_path"
        const val INTENT_EXTRA_MATCH_MODE = "intent_extra_match_mode"
    }

    private lateinit var viewModel: ScannerViewModel
    private lateinit var binding: ActivityScannerBinding

    private lateinit var dm: DupeManager
    private lateinit var adapter: SectionedRecyclerViewAdapter

    private lateinit var path: String
    private var matchMode = -1
    private var sortMode = 0 // TODO : update

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_scanner)
        viewModel = ViewModelProvider(this).get(ScannerViewModel::class.java)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        path = intent.getStringExtra(INTENT_EXTRA_SCAN_PATH) ?: run { finish(); return; }
        matchMode = intent.getIntExtra(INTENT_EXTRA_MATCH_MODE, -1)
        if (matchMode < 0) {
            finish(); return;
        }

        setupListView()

        viewModel.totalScanned.observe(this) {
            binding.tvStatus.text =
                "Total ${viewModel.totalScanned.value} scanned, ${viewModel.totalDupes.value} found"
        }
        viewModel.totalDupes.observe(this) {
            binding.tvStatus.text =
                "Total ${viewModel.totalScanned.value} scanned, ${viewModel.totalDupes.value} found"
        }

        binding.btnSort.setOnClickListener { sort() }
        binding.btnSortReverse.setOnClickListener { sort(true) }

        binding.btnMove.setOnClickListener { move() }
        binding.btnClear.setOnClickListener { clear() }
    }

    private fun setupListView() {
        adapter = SectionedRecyclerViewAdapter()
        binding.rv.layoutManager = LinearLayoutManager(this@ScannerActivity)
        binding.rv.adapter = adapter

        // Add sections
        var sections = 0
        Log.e("SCAN", "launching scanner with $path")
        dm = DupeManager(this, lifecycleScope, path, matchMode, sortMode)
        dm.scan { duplicates, totalScanned, totalDuplicates ->
            Log.e("SCAN", "scanned $totalScanned, found $totalDuplicates")

            duplicates.forEach {
                adapter.addSection(MusicSection(it))
            }

            runOnUiThread {
                adapter.notifyDataSetChanged()
                viewModel.totalScanned.value = totalScanned
                viewModel.totalDupes.value = totalDuplicates
                viewModel.isScanDone.value = true
            }
        }
    }

    private fun sort(reverse: Boolean = false) {
        viewModel.isScanDone.value = false
        dm.sortMode = viewModel.sortMode.value!!
        dm.sort(reverse) { duplicates ->
            runOnUiThread {
                adapter.notifyDataSetChanged()
                viewModel.isScanDone.value = true
                // TODO : Reset checked state in sections
            }
        }
    }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult

            val targetDir =
                DocumentFileCompat.fromUri(this, uri) ?: run {
                    Log.e("Launcher callback", "uri is null")
                    return@registerForActivityResult
                }
            var success = 0
            var failure = 0
            var skipped = 0
            val fileCallback = object : FileCallback() {
                override fun onFailed(errorCode: ErrorCode) {
                    ++failure
                    Log.e("fileCallback", "Failed")
                }

                override fun onCompleted(result: Any) {
                    ++success
                    Log.e("fileCallback", "Completed")
                }

                override fun onConflict(destinationFile: DocumentFile, action: FileConflictAction) {
                    action.confirmResolution(ConflictResolution.SKIP)
                    Log.e("fileCallback", "Skipped")
                    ++skipped
                }
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val context = this@ScannerActivity
                for (i in 0 until adapter.sectionCount) {
                    val section = adapter.getSection(i)
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
                                        parentDir.substring(startIndex = path.length /*+ 1*/)

//                                    val appendix1 = targetDir.getAbsolutePath(context)
//                                    val appendix2 = subDir!!.getAbsolutePath(context)
//                                    Log.e("APPENDIX", "1:$appendix1, 2:$appendix2")
                                    val moveDirPath =
                                        targetDir.getAbsolutePath(context) + '/' + subDir
                                    if (!DocumentFileCompat.doesExist(context,moveDirPath)) DocumentFileCompat.mkdirs(context, moveDirPath)

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
                }
                launch(Dispatchers.Main) {
                    AlertDialog.Builder(context)
                        .setTitle("Done")
                        .setMessage(
                            """
                            Success: $success
                            Failure: $failure
                            Skipped: $skipped
                        """.trimIndent()
                        )
                        .setPositiveButton("OK", null)
                        .setCancelable(false)
                        .show()
                }
            }
        }

    private fun move() {
        folderPickerLauncher.launch(null)
    }

    private fun clear() {

    }
}