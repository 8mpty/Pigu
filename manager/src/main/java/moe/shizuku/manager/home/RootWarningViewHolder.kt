package moe.shizuku.manager.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeRootWarningBinding
import moe.shizuku.manager.utils.RootWarning
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

/**
 * Persistent, non-dismissible reminder that the server is running as root. The one-time
 * confirmation before the first root start is separate; this card stays as long as root mode is
 * active so the user does not forget that every authorized app has root-equivalent access.
 */
class RootWarningViewHolder(
    binding: HomeRootWarningBinding,
    root: View,
) : BaseViewHolder<Any?>(root) {

    companion object {
        val CREATOR = Creator<Any?> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeRootWarningBinding.inflate(inflater, outer.root, true)
            RootWarningViewHolder(inner, outer.root)
        }
    }

    init {
        root.setOnClickListener {
            MaterialAlertDialogBuilder(it.context)
                .setTitle(R.string.root_banner_dialog_title)
                .setMessage(R.string.root_banner_dialog_message)
                .setPositiveButton(R.string.root_warning_continue) { _, _ ->
                    RootWarning.markAcknowledged()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
