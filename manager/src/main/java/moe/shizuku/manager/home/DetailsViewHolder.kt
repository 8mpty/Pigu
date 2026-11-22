package moe.shizuku.manager.home

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.databinding.HomeDetailRowBinding
import moe.shizuku.manager.databinding.HomeDetailsBinding
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.CompanionApp
import moe.shizuku.manager.utils.EnvironmentUtils
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

data class DetailsData(
    val status: ServiceStatus,
    val authorizedApps: Int,
    val totalApps: Int,
    val expanded: Boolean = false,
    val onToggle: () -> Unit = {},
)

class DetailsViewHolder(
    private val binding: HomeDetailsBinding,
    root: View
) : BaseViewHolder<DetailsData>(root) {

    companion object {
        val CREATOR = Creator<DetailsData> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeDetailsBinding.inflate(inflater, outer.root, true)
            DetailsViewHolder(inner, outer.root)
        }
    }

    init {
        root.setOnClickListener {
            val details = data ?: return@setOnClickListener
            val updated = details.copy(expanded = !details.expanded)
            // Update this holder directly instead of going through notifyItemChanged(). That
            // avoids the change animation/rebind race that made rapid expand/collapse taps feel
            // unresponsive.
            setData(updated)
            bindDetails(updated)
            details.onToggle()
        }
    }

    override fun onBind() {
        bindDetails(data ?: DetailsData(ServiceStatus(), 0, 0))
    }

    private fun bindDetails(details: DetailsData) {
        val context = itemView.context
        val status = details.status
        val enabled = context.getString(R.string.details_enabled)
        val disabled = context.getString(R.string.details_disabled)
        val unavailable = context.getString(R.string.details_unavailable)

        binding.summary.isVisible = !details.expanded
        binding.rows.isVisible = details.expanded
        binding.expandIcon.rotation = if (details.expanded) 180f else 0f
        ViewCompat.setStateDescription(
            itemView,
            context.getString(
                if (details.expanded) R.string.details_collapse_action
                else R.string.details_expand_action
            )
        )

        binding.rows.removeAllViews()
        if (!details.expanded) {
            itemView.requestLayout()
            return
        }

        val inflater = LayoutInflater.from(context)

        fun add(label: Int, value: CharSequence) {
            val row = HomeDetailRowBinding.inflate(inflater, binding.rows, false)
            row.label.setText(label)
            row.value.text = value
            binding.rows.addView(row.root)
        }

        add(R.string.details_pigu_version, BuildConfig.VERSION_NAME)

        add(
            R.string.details_shizuku_version,
            if (status.isRunning) "${status.apiVersion}.${status.patchVersion}"
            else context.getString(R.string.details_not_running)
        )

        add(
            R.string.details_android_version,
            "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )

        add(
            R.string.details_abi,
            Build.SUPPORTED_ABIS.firstOrNull() ?: context.getString(R.string.details_pairing_unknown)
        )

        val pairing = when {
            !status.isRunning -> unavailable
            status.uid == 0 -> context.getString(R.string.details_pairing_root)
            ShizukuSettings.getLastLaunchMode() == ShizukuSettings.LaunchMethod.WIRELESS_ADB ->
                context.getString(R.string.details_pairing_wireless)
            ShizukuSettings.getLastLaunchMode() == ShizukuSettings.LaunchMethod.ADB ->
                context.getString(R.string.details_pairing_adb)
            else -> context.getString(R.string.details_pairing_unknown)
        }
        add(R.string.details_pairing_method, pairing)

        add(
            R.string.details_authorized_apps,
            if (status.isRunning) "${details.authorizedApps}/${details.totalApps}" else unavailable
        )

        val companion = CompanionApp.status(context)
        add(
            R.string.details_companion,
            when {
                !companion.installed -> context.getString(R.string.details_companion_not_installed)
                !companion.signedByPigu -> context.getString(R.string.details_companion_other)
                else -> context.getString(R.string.details_companion_installed)
            }
        )

        add(
            R.string.details_start_on_boot,
            if (ShizukuSettings.getStartOnBoot(context)) enabled else disabled
        )

        val tcpMode = ShizukuSettings.getTcpMode()
        add(R.string.details_tcp_mode, if (tcpMode) enabled else disabled)

        val tcpPort = if (tcpMode) {
            EnvironmentUtils.getAdbTcpPort().takeIf { it > 0 } ?: ShizukuSettings.getTcpPort()
        } else null
        add(R.string.details_tcp_port, tcpPort?.toString() ?: unavailable)
        itemView.requestLayout()
    }
}
