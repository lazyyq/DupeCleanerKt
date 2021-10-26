package kyklab.dupecleanerkt.ui.scanner

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.fragment.app.DialogFragment
import kyklab.dupecleanerkt.R
import kyklab.dupecleanerkt.utils.checkButtonAtIndex
import kyklab.dupecleanerkt.utils.checkedButtonIndex

class SortOptionsDialogFragment(
    private val listener: SortOptionsDialogListener,
    private val sortSectionsOption: ScannerActivity.SortSectionsOptions? = null,
    private val sortItemsOption: ScannerActivity.SortItemsOptions? = null
) :
    DialogFragment() {
    private var width = 0f

    interface SortOptionsDialogListener {
        fun onDialogPositiveClick(
            dialog: DialogFragment,
            sortSectionsOption: ScannerActivity.SortSectionsOptions?,
            sortItemsOption: ScannerActivity.SortItemsOptions?
        )

        fun onDialogNegativeClick(
            dialog: DialogFragment,
            sortSectionsOption: ScannerActivity.SortSectionsOptions?,
            sortItemsOption: ScannerActivity.SortItemsOptions?
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

            // Check previously checked options by default
            sortSectionsOption?.let { option ->
                val index = ScannerActivity.SortSectionsOptions.valueOf(option.name).ordinal
                view.findViewById<RadioGroup>(R.id.rgSections).checkButtonAtIndex(index)
            }
            sortItemsOption?.let { option ->
                val index = ScannerActivity.SortItemsOptions.valueOf(option.name).ordinal
                view.findViewById<RadioGroup>(R.id.rgItems).checkButtonAtIndex(index)
            }

            builder.setView(view)
                .setPositiveButton(android.R.string.ok) { dialogInterface, i ->
                    val (sortSectionsOption, sortItemsOption) = getSelection(view)
                    listener.onDialogPositiveClick(this, sortSectionsOption, sortItemsOption)
                }
                .setNegativeButton(android.R.string.cancel) { dialogInterface, i ->
                    val (sortSectionsOption, sortItemsOption) = getSelection(view)
                    listener.onDialogNegativeClick(this, sortSectionsOption, sortItemsOption)
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun getSelection(view: View): Pair<ScannerActivity.SortSectionsOptions?, ScannerActivity.SortItemsOptions?> {
        val sortSectionsOption =
            (view.findViewById<RadioGroup>(R.id.rgSections).checkedButtonIndex).let {
                if (it > -1) ScannerActivity.SortSectionsOptions.values()[it] else null
            }
        val sortItemsOption = (view.findViewById<RadioGroup>(R.id.rgItems).checkedButtonIndex).let {
            if (it > -1) ScannerActivity.SortItemsOptions.values()[it] else null
        }

        return Pair(sortSectionsOption, sortItemsOption)
    }
}