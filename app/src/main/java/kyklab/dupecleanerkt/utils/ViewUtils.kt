package kyklab.dupecleanerkt.utils

import android.widget.RadioButton
import android.widget.RadioGroup

inline val RadioGroup.checkedButtonIndex: Int
    get() = indexOfChild(findViewById(checkedRadioButtonId))

fun RadioGroup.checkButtonAtIndex(index: Int) {
    (getChildAt(index) as? RadioButton)?.isChecked = true
}
