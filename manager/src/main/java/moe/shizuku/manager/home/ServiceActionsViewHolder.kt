package moe.shizuku.manager.home

import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeServiceActionsBinding
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants

/** The home service card: status summary plus the actions that apply in that state. */
class ServiceActionsViewHolder(
    private val binding: HomeServiceActionsBinding,
    root: View,
) : BaseViewHolder<ServiceStatus>(root) {

    companion object {
        val CREATOR = Creator<ServiceStatus> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeServiceActionsBinding.inflate(inflater, outer.root, true)
            ServiceActionsViewHolder(inner, outer.root)
        }
    }

    /** The card stays on the normal theme surface; only the state emblem carries the status color. */
    private val normalTitleColors = binding.statusTitle.textColors

    init {
        binding.buttonStop.setOnClickListener {
            val context = it.context
            if (!ShizukuStateMachine.isRunning()) return@setOnClickListener

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_stop_title)
                .setMessage(R.string.dialog_stop_message_reactivate)
                .setPositiveButton(R.string.stop_service) { _, _ ->
                    // Suppress watchdog recovery before asking the server to exit, or the
                    // binder death from this deliberate stop is indistinguishable from a crash.
                    ShizukuSettings.setManuallyStopped(true)
                    ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
                    runCatching { Shizuku.exit() }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.buttonStart.setOnClickListener {
            it.context.startActivity(Intent(it.context, StartMethodsActivity::class.java))
        }

        binding.buttonPairingMethods.setOnClickListener {
            it.context.startActivity(Intent(it.context, StartMethodsActivity::class.java))
        }
    }

    override fun onBind() {
        val status = data ?: ServiceStatus()
        val state = ShizukuStateMachine.get()

        val (labelRes, iconRes, colorRes) = when (state) {
            ShizukuStateMachine.State.RUNNING ->
                Triple(R.string.home_status_label_running, R.drawable.ic_server_ok_24dp, R.color.home_status_running)
            ShizukuStateMachine.State.STARTING ->
                Triple(R.string.home_status_label_starting, R.drawable.ic_server_restart, R.color.home_status_warning)
            ShizukuStateMachine.State.STOPPING ->
                Triple(R.string.home_status_label_stopping, R.drawable.ic_server_restart, R.color.home_status_warning)
            ShizukuStateMachine.State.CRASHED ->
                Triple(R.string.home_status_label_error, R.drawable.ic_server_error_24dp, R.color.home_status_warning)
            else ->
                Triple(R.string.home_status_label_stopped, R.drawable.ic_server_error_24dp, R.color.home_status_stopped)
        }
        val color = ContextCompat.getColor(itemView.context, colorRes)
        val normalDetailColor = binding.statusDetail.textColors.defaultColor

        binding.statusTitle.setText(labelRes)
        binding.statusTitle.setTextColor(normalTitleColors)
        binding.statusIcon.setImageResource(iconRes)
        binding.statusIcon.imageTintList = ColorStateList.valueOf(color)
        binding.statusIcon.backgroundTintList =
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33))

        val running = state == ShizukuStateMachine.State.RUNNING
        val canStart = state == ShizukuStateMachine.State.STOPPED ||
            state == ShizukuStateMachine.State.CRASHED

        binding.statusActions.isVisible = running
        binding.buttonStop.isVisible = running
        binding.buttonStart.isVisible = canStart
        binding.buttonPairingMethods.isVisible = running

        when {
            state == ShizukuStateMachine.State.STARTING -> {
                binding.statusDetail.setText(R.string.home_service_detail_starting)
                binding.statusDetail.setTextColor(normalDetailColor)
            }
            state == ShizukuStateMachine.State.STOPPING -> {
                binding.statusDetail.setText(R.string.home_service_detail_stopping)
                binding.statusDetail.setTextColor(normalDetailColor)
            }
            state == ShizukuStateMachine.State.CRASHED -> {
                binding.statusDetail.setText(R.string.home_service_detail_error)
                binding.statusDetail.setTextColor(normalDetailColor)
            }
            running -> {
                val outdated = status.apiVersion >= 0 && (
                    status.apiVersion != Shizuku.getLatestServiceVersion() ||
                        status.patchVersion != ShizukuApiConstants.SERVER_PATCH_VERSION
                    )
                when {
                    outdated -> {
                        binding.statusDetail.text = itemView.context.getString(
                            R.string.home_status_update_hint,
                            "${Shizuku.getLatestServiceVersion()}.${ShizukuApiConstants.SERVER_PATCH_VERSION}",
                        )
                        binding.statusDetail.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.home_status_warning)
                        )
                    }
                    status.apiVersion >= 0 -> {
                        binding.statusDetail.text = itemView.context.getString(
                            R.string.home_service_detail_running,
                            "${status.apiVersion}.${status.patchVersion}",
                            if (status.uid == 0) "root" else "adb",
                        )
                        binding.statusDetail.setTextColor(normalDetailColor)
                    }
                    else -> {
                        binding.statusDetail.setText(R.string.home_service_detail_ready)
                        binding.statusDetail.setTextColor(normalDetailColor)
                    }
                }
            }
            else -> {
                binding.statusDetail.setText(R.string.home_service_detail_stopped)
                binding.statusDetail.setTextColor(normalDetailColor)
            }
        }
    }
}
