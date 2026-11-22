package moe.shizuku.manager.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import moe.shizuku.manager.databinding.DeveloperSectionHeaderBinding
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

/** Full-width heading that separates groups of cards on the Developer page. */
class DeveloperSectionViewHolder(
    private val binding: DeveloperSectionHeaderBinding,
) : BaseViewHolder<Int>(binding.root) {

    companion object {
        val CREATOR = Creator<Int> { inflater: LayoutInflater, parent: ViewGroup? ->
            DeveloperSectionViewHolder(
                DeveloperSectionHeaderBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBind() {
        binding.sectionTitle.setText(data)

        // Keep the heading on its own row when the page is a two-column grid (tablet/TV).
        val params = itemView.layoutParams
        if (params is StaggeredGridLayoutManager.LayoutParams) {
            params.isFullSpan = true
        }
    }
}
