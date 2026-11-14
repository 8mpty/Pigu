package moe.shizuku.manager.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        binding.text2.text = when {
            !status.installed ->
                context.getString(R.string.companion_summary_not_installed, CompanionApp.PACKAGE)
            !status.signedByPigu ->
                context.getString(R.string.companion_summary_other, CompanionApp.PACKAGE)
            else ->
                context.getString(
                    R.string.companion_summary_installed,
                    CompanionApp.PACKAGE,
                    status.versionName ?: ""
                )
        }

        itemView.setOnClickListener { showDialog(context, status) }
    }

    private fun showDialog(context: android.content.Context, status: CompanionApp.Status) {
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
