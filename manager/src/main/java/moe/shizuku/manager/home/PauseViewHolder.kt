package moe.shizuku.manager.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomePauseBinding
import moe.shizuku.manager.security.AccessPause
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

/**
 * Shown while the global pause is active. The resume action is deliberately on the home screen,
 * not buried in Settings, because this is the recovery path for a kill switch.
 */
class PauseViewHolder(
    private val binding: HomePauseBinding,
    root: View,
) : BaseViewHolder<Any?>(root) {

    companion object {
        val CREATOR = Creator<Any?> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomePauseBinding.inflate(inflater, outer.root, true)
            PauseViewHolder(inner, outer.root)
        }
    }

    init {
        binding.buttonResume.setOnClickListener {
            AccessPause.resume(it.context)
            (adapter as? HomeAdapter)?.updateData()
        }
    }
}
