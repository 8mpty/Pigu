package moe.shizuku.manager.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.CompanionItemBinding
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.utils.CompanionApp
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class CompanionViewHolder(
    private val binding: CompanionItemBinding,
    root: View
) : BaseViewHolder<CompanionApp.Status>(root) {

    companion object {
        val CREATOR = Creator<CompanionApp.Status> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = CompanionItemBinding.inflate(inflater, outer.root, true)
            CompanionViewHolder(inner, outer.root)
        }
    }

    override fun onBind() {
        val context = itemView.context
        val status = data ?: CompanionApp.Status(false, null, false)
        val version = status.versionName.orEmpty()

        val appIcon = status.icon
        binding.icon.isVisible = appIcon != null
        binding.placeholderIcon.isVisible = appIcon == null
        appIcon?.let { binding.icon.setImageDrawable(it) }

        binding.text1.text = status.label ?: context.getString(R.string.companion_title)
        binding.text2.text = CompanionApp.PACKAGE
        binding.text3.text = when {
            !status.installed -> context.getString(R.string.companion_summary_not_installed)
            !status.signedByPigu -> if (version.isNotBlank())
                context.getString(R.string.companion_summary_other, version)
            else context.getString(R.string.details_companion_other)
            else -> if (version.isNotBlank())
                context.getString(R.string.companion_summary_installed, version)
            else context.getString(R.string.details_companion_installed)
        }

        itemView.setOnClickListener { showDialog(context, status) }
    }

    private fun showDialog(context: Context, status: CompanionApp.Status) {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.companion_title)
            .setMessage(context.getString(R.string.companion_dialog_message, context.packageName, CompanionApp.PACKAGE))

        if (status.installed) {
            builder.setPositiveButton(R.string.companion_open_settings) { _, _ ->
                CompanionApp.openAppInfo(context)
            }
            builder.setNegativeButton(R.string.companion_close, null)
        } else {
            builder.setPositiveButton(R.string.companion_close, null)
        }

        builder.show()
    }
}
