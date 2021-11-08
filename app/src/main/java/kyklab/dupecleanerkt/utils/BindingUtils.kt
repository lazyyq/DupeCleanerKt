package kyklab.dupecleanerkt.utils

import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.annotation.LayoutRes
import androidx.core.widget.doAfterTextChanged
import androidx.databinding.BindingAdapter
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator

@BindingAdapter("visibility")
fun setVisibility(view: View, value: Boolean) {
    view.visibility = if (value) View.VISIBLE else View.GONE
}

@BindingAdapter("circularProgressIndicatorVisibility")
fun setCircularProgressIndicatorVisibility(progressBar: CircularProgressIndicator, value: Boolean) {
    if (value) progressBar.show() else progressBar.hide()
}

@BindingAdapter("linearProgressIndicatorVisibility")
fun setLinearProgressIndicatorVisibility(progressBar: LinearProgressIndicator, value: Boolean) {
    if (value) progressBar.show() else progressBar.hide()
}

@BindingAdapter("layout", "entries", "defaultEntry", "onItemSelected", requireAll = true)
fun AutoCompleteTextView.setAdapter(
    @LayoutRes layout: Int,
    items: Array<Any>,
    defaultEntry: Int,
    listener: DropdownMenuItemSelectedListener
) {
    Log.e("ADAPTER", "called, but should be calle donce")
    setText(items[defaultEntry].toString(), false)
    listener.onItemSelected(defaultEntry)
    setAdapter(ArrayAdapter(context, layout, items))

    doAfterTextChanged { text ->
        Log.e("binding", "onTextChange called")
        var selected = -1
        for (i in 0 until (adapter?.count ?: 0)) {
            val item = adapter.getItem(i) as? String ?: continue
            if (item == text.toString()) {
                selected = i
                Log.e("Spinner", "selected index is $i")
                break
            }
        }
        listener.onItemSelected(selected)
    }
}

interface DropdownMenuItemSelectedListener {
    fun onItemSelected(index: Int)
}
