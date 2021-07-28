package kyklab.dupecleanerkt.utils

import android.view.View
import android.widget.ProgressBar
import androidx.databinding.BindingAdapter
import com.google.android.material.progressindicator.BaseProgressIndicator
import com.google.android.material.progressindicator.BaseProgressIndicatorSpec
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator

@BindingAdapter("visibility")
fun setVisibility(view: View, value: Boolean) {
    view.visibility = if (value) View.VISIBLE else View.INVISIBLE
}

@BindingAdapter("circularProgressIndicatorVisibility")
fun setCircularProgressIndicatorVisibility(progressBar: CircularProgressIndicator, value: Boolean) {
    if (value) progressBar.show() else progressBar.hide()
}

@BindingAdapter("linearProgressIndicatorVisibility")
fun setLinearProgressIndicatorVisibility(progressBar: LinearProgressIndicator, value: Boolean) {
    if (value) progressBar.show() else progressBar.hide()
}