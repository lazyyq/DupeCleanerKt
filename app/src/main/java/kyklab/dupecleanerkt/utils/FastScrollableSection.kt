package kyklab.dupecleanerkt.utils

import io.github.luizgrp.sectionedrecyclerviewadapter.Section
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionParameters

abstract class FastScrollableSection(sectionParameters: SectionParameters) :
    Section(sectionParameters) {

    abstract fun getItemInitial(position: Int): CharSequence
}