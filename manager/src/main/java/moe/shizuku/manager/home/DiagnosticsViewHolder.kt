package moe.shizuku.manager.home

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.HomeDiagnosticsBinding
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.CompanionApp
import moe.shizuku.manager.utils.PiguDiagnostics
import rikka.core.util.ClipboardUtils
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

data class DiagnosticsData(
    val status: ServiceStatus,
    val grantedCount: Int,
    val companion: CompanionApp.Status,
)

class DiagnosticsViewHolder(
    binding: HomeDiagnosticsBinding,
    root: View,
) : BaseViewHolder<DiagnosticsData>(root) {

    companion object {
        val CREATOR = Creator<DiagnosticsData> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeDiagnosticsBinding.inflate(inflater, outer.root, true)
            DiagnosticsViewHolder(inner, outer.root)
        }
    }

    init {
        root.setOnClickListener { view ->
            val details = data ?: return@setOnClickListener
            val snapshot = PiguDiagnostics.build(
                view.context,
                details.status,
                details.grantedCount,
                details.companion,
            )
            if (ClipboardUtils.put(view.context, snapshot) &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
            ) {
                Toast.makeText(
                    view.context,
                    R.string.toast_copied_to_clipboard,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
