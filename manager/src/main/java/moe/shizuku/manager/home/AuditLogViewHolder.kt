package moe.shizuku.manager.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import moe.shizuku.manager.R
import moe.shizuku.manager.audit.AuditLogSheet
import moe.shizuku.manager.databinding.HomeAuditLogBinding
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

data class AuditLogData(
    val count: Int,
    val onChanged: () -> Unit,
)

class AuditLogViewHolder(
    private val binding: HomeAuditLogBinding,
    root: View,
) : BaseViewHolder<AuditLogData>(root) {

    companion object {
        val CREATOR = Creator<AuditLogData> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeAuditLogBinding.inflate(inflater, outer.root, true)
            AuditLogViewHolder(inner, outer.root)
        }
    }

    init {
        root.setOnClickListener {
            val info = data ?: return@setOnClickListener
            AuditLogSheet.show(it.context) { info.onChanged() }
        }
    }

    override fun onBind() {
        binding.text2.text = itemView.context.resources.getQuantityString(
            R.plurals.audit_log_card_summary,
            data.count,
            data.count,
        )
    }
}
