package moe.shizuku.manager.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import moe.shizuku.manager.accessibility.AccessibilityActivity
import moe.shizuku.manager.databinding.HomeAccessibilityItemBinding
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

/** Developer > Tools entry for the read-only accessibility service list. */
class AccessibilityCardViewHolder(
    binding: HomeAccessibilityItemBinding,
    root: View,
) : BaseViewHolder<Any?>(root) {

    companion object {
        val CREATOR = Creator<Any> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeAccessibilityItemBinding.inflate(inflater, outer.root, true)
            AccessibilityCardViewHolder(inner, outer.root)
        }
    }

    init {
        root.setOnClickListener {
            it.context.startActivity(Intent(it.context, AccessibilityActivity::class.java))
        }
    }
}
