package moe.shizuku.manager.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.HomeAppsBinding
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.management.ApplicationManagementActivity
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

data class HomeAppsData(
    val authorized: Int,
    val total: Int,
)

/** A compact summary of the apps that are allowed to talk to Pigu. */
class HomeAppsViewHolder(
    private val binding: HomeAppsBinding,
    root: View,
) : BaseViewHolder<HomeAppsData>(root) {

    companion object {
        val CREATOR = Creator<HomeAppsData> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeAppsBinding.inflate(inflater, outer.root, true)
            HomeAppsViewHolder(inner, outer.root)
        }
    }

    init {
        itemView.setOnClickListener {
            it.context.startActivity(Intent(it.context, ApplicationManagementActivity::class.java))
        }
    }

    override fun onBind() {
        val context = itemView.context
        val info = data ?: return

        val summary = if (info.authorized <= 0) {
            context.getString(R.string.home_apps_empty)
        } else {
            context.resources.getQuantityString(
                R.plurals.home_apps_summary,
                info.total,
                info.authorized,
                info.total,
            )
        }
        binding.appsSummary.text = summary
        itemView.contentDescription = context.getString(R.string.home_apps_a11y, summary)
    }
}
