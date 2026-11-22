package moe.shizuku.manager.home

import android.content.Intent
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeManageAppsItemBinding
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.management.AppDiscoverySummary
import moe.shizuku.manager.management.ApplicationManagementActivity
import moe.shizuku.manager.model.ServiceStatus
import rikka.html.text.HtmlCompat
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

data class ManageAppsData(
    val status: ServiceStatus,
    val summary: AppDiscoverySummary,
)

class ManageAppsViewHolder(private val binding: HomeManageAppsItemBinding, root: View) :
    BaseViewHolder<ManageAppsData>(root), View.OnClickListener {

    companion object {
        val CREATOR = Creator<ManageAppsData> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeManageAppsItemBinding.inflate(inflater, outer.root, true)
            ManageAppsViewHolder(inner, outer.root)
        }
    }

    init {
        root.setOnClickListener(this)
    }

    private inline val title get() = binding.text1
    private inline val summary get() = binding.text2

    override fun onBind() {
        val context = itemView.context
        val info = data
        if (!info.status.isRunning) {
            itemView.isEnabled = false
            title.setText(R.string.home_app_management_title)
            summary.text = context.getString(
                R.string.home_status_service_not_running,
                context.getString(R.string.app_name)
            )
        } else {
            itemView.isEnabled = true
            title.text = context.resources.getQuantityString(
                R.plurals.home_app_management_authorized_apps_count,
                info.summary.authorized,
                info.summary.authorized
            )
            summary.text = when {
                info.summary.total == 0 -> context.getString(R.string.home_app_management_empty)
                info.summary.companionCandidates > 0 -> context.resources.getQuantityString(
                    R.plurals.home_app_management_companion_candidates,
                    info.summary.companionCandidates,
                    info.summary.companionCandidates
                )
                else -> context.getString(R.string.home_app_management_view_authorized_apps)
            }
        }
    }

    override fun onClick(v: View) {
        v.context.startActivity(Intent(v.context, ApplicationManagementActivity::class.java))
    }
}
