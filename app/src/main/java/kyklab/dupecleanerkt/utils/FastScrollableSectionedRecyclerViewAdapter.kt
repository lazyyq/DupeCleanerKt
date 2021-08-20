package kyklab.dupecleanerkt.utils

import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import io.github.luizgrp.sectionedrecyclerviewadapter.Section
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter

class FastScrollableSectionedRecyclerViewAdapter : SectionedRecyclerViewAdapter(),
    RecyclerViewFastScroller.OnPopupTextUpdate {

    override fun onChange(position: Int): CharSequence {
        val section = getSectionForPosition(position)
        return (section as? FastScrollableSection)?.getItemInitial(position) ?: " "
    }
}