package kyklab.dupecleanerkt.utils

import android.view.View
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