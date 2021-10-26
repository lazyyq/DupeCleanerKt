package kyklab.dupecleanerkt.ui.scanner

import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionParameters
import kyklab.dupecleanerkt.R
import kyklab.dupecleanerkt.data.Music
import kyklab.dupecleanerkt.utils.FastScrollableSection
import kyklab.dupecleanerkt.utils.getAlbumArtUri

class MusicSection(
    val list: List<Music>,
    private val itemCheckChangeListener: ItemCheckChangeListener? = null,
) : FastScrollableSection(
    SectionParameters.builder()
        .itemResourceId(R.layout.music_section_item)
        .headerResourceId(R.layout.music_section_header)
        .build()
) {
    companion object {
        const val PAYLOAD_TRIGGER_CHECKBOX_STATE_UPDATE = "trigger"
    }

    val checkedIndexes = Array(list.size) { it != 0 /* Leave first item unchecked */ }

    private val itemInitial = (list.getOrNull(0)?.title?.get(0) ?: ' ').toString()

    override fun getContentItemsTotal() = list.size

    override fun getItemViewHolder(view: View?) = MyItemViewHolder(view!!)

    override fun onBindItemViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        (holder as? MyItemViewHolder)?.apply {
            val music = list[position]
            tvTitle.text = music.title
            tvArtistAndAlbum.text = music.getArtistAndAlbumText()
            tvDuration.text =
                "${music.durationString}  |  Last modified at ${music.dateModifiedString} | ${music.dateModified}"
            tvPath.text = music.path

            cb.isChecked = checkedIndexes[position]
            cb.setOnClickListener {
                cbChecked(cb, position)
            }
            cardView.setOnClickListener {
                cb.isChecked = !cb.isChecked
                cbChecked(cb, position)
            }
        }
    }

    override fun onBindItemViewHolder(
        holder: RecyclerView.ViewHolder?,
        position: Int,
        payloads: MutableList<Any>?,
    ) {
        if (payloads?.isNotEmpty() == true) {
            (holder as? MyItemViewHolder)?.apply {
                payloads.forEach { payload ->
                    if (payload is String && payload == PAYLOAD_TRIGGER_CHECKBOX_STATE_UPDATE) {
                        holder.cb.isChecked = checkedIndexes[position]
                    }
                }
            }
        } else {
            super.onBindItemViewHolder(holder, position, payloads)
        }
    }

    override fun onBindHeaderViewHolder(holder: RecyclerView.ViewHolder?) {
        (holder as? MyHeaderViewHolder)?.apply {
            val music = list.first()

            Glide.with(ivAlbumArt)
                .load(getAlbumArtUri(music.albumId))
                .placeholder(R.drawable.ic_album)
                .into(ivAlbumArt)
            tvTitle.text = music.title
            tvArtistAndAlbum.text = music.getArtistAndAlbumText()
        }
    }

    override fun getHeaderViewHolder(view: View?) = MyHeaderViewHolder(view!!)

    private fun Music.getArtistAndAlbumText() =
        "${if (artist.isNotEmpty()) artist else "Unknown artist"} - ${if (album.isNotEmpty()) album else "Unknown album"}"

    fun interface ItemCheckChangeListener {
        fun onItemCheckChanged(
            section: MusicSection?, itemAdapterPosition: Int, newState: Boolean,
        )
    }

    class MyHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAlbumArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvArtistAndAlbum: TextView = itemView.findViewById(R.id.tvArtistAndAlbum)
    }

    class MyItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val cb: CheckBox = itemView.findViewById(R.id.cb)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvArtistAndAlbum: TextView = itemView.findViewById(R.id.tvArtistAndAlbum)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val tvPath: TextView = itemView.findViewById(R.id.tvPath)
    }

    private fun cbChecked(checkBox: CheckBox, position: Int) {
        checkedIndexes[position] = checkBox.isChecked
        itemCheckChangeListener?.onItemCheckChanged(
            this@MusicSection, position, checkBox.isChecked
        )
    }

    override fun getItemInitial(position: Int) = itemInitial
}