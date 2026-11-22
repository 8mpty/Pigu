package moe.shizuku.manager.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.CompanionBottomSheetBinding
import moe.shizuku.manager.databinding.CompanionItemBinding
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.utils.CompanionApp
import moe.shizuku.manager.utils.CompanionInstaller
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

        val appIcon = status.icon
        binding.icon.isVisible = appIcon != null
        binding.placeholderIcon.isVisible = appIcon == null
        appIcon?.let { binding.icon.setImageDrawable(it) }

        binding.text1.text = status.label ?: context.getString(R.string.companion_title)
        binding.text2.text = CompanionApp.PACKAGE
        binding.text3.text = statusText(context, status)

        itemView.setOnClickListener { showDialog(context, status) }
    }

    private fun statusText(context: Context, status: CompanionApp.Status): String {
        val version = status.versionName.orEmpty()
        return when {
            !status.installed -> context.getString(R.string.companion_summary_not_installed)
            !status.signedByPigu -> if (version.isNotBlank())
                context.getString(R.string.companion_summary_other, version)
            else context.getString(R.string.details_companion_other)
            else -> if (version.isNotBlank())
                context.getString(R.string.companion_summary_installed, version)
            else context.getString(R.string.details_companion_installed)
        }
    }

    private fun showDialog(context: Context, status: CompanionApp.Status) {
        val sheetBinding = CompanionBottomSheetBinding.inflate(LayoutInflater.from(context))
        val sheet = BottomSheetDialog(context).apply {
            setContentView(sheetBinding.root)
            behavior.skipCollapsed = true
        }

        val appIcon = status.icon
        sheetBinding.icon.isVisible = appIcon != null
        sheetBinding.placeholderIcon.isVisible = appIcon == null
        appIcon?.let { sheetBinding.icon.setImageDrawable(it) }

        sheetBinding.title.text = status.label ?: context.getString(R.string.companion_title)
        sheetBinding.packageName.text = CompanionApp.PACKAGE
        sheetBinding.status.text = if (!status.installed) {
            context.getString(R.string.details_companion_not_installed)
        } else {
            statusText(context, status)
        }
        sheetBinding.description.setText(
            when {
                !status.installed -> R.string.companion_sheet_description
                status.signedByPigu -> R.string.companion_sheet_installed_description
                else -> R.string.companion_sheet_other_description
            }
        )

        sheetBinding.info.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.companion_title)
                .setMessage(
                    context.getString(
                        R.string.companion_dialog_message,
                        context.packageName,
                        CompanionApp.PACKAGE
                    )
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        if (status.installed) {
            sheetBinding.install.isVisible = false
            sheetBinding.systemInstaller.isVisible = false
            sheetBinding.appInfo.isVisible = true
            sheetBinding.appInfo.setOnClickListener {
                sheet.dismiss()
                CompanionApp.openAppInfo(context)
            }
        } else {
            sheetBinding.install.isVisible = true
            sheetBinding.systemInstaller.isVisible = true
            sheetBinding.appInfo.isVisible = false
            sheetBinding.install.setOnClickListener {
                sheet.dismiss()
                install(context, useSystemInstaller = false)
            }
            sheetBinding.systemInstaller.setOnClickListener {
                sheet.dismiss()
                install(context, useSystemInstaller = true)
            }
        }

        sheetBinding.close.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun install(context: Context, useSystemInstaller: Boolean) {
        val activity = context.findActivity()
        val owner = itemView.findViewTreeLifecycleOwner()
        val scope = owner?.lifecycleScope ?: (activity as? LifecycleOwner)?.lifecycleScope
        if (scope == null) {
            Toast.makeText(context, R.string.companion_install_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val progress = MaterialAlertDialogBuilder(context)
            .setMessage(R.string.companion_installing)
            .setCancelable(false)
            .create()
        progress.show()

        scope.launch {
            val result = try {
                if (useSystemInstaller) {
                    CompanionInstaller.installWithSystemInstaller(context)
                } else {
                    CompanionInstaller.install(context)
                }
            } finally {
                progress.dismiss()
            }

            when (result) {
                CompanionInstaller.Result.Success -> {
                    Toast.makeText(context, R.string.companion_install_success, Toast.LENGTH_SHORT).show()
                    (context.findActivity() as? DeveloperActivity)?.refreshCompanion()
                }

                CompanionInstaller.Result.SystemInstallerOpened -> {
                    Toast.makeText(
                        context,
                        R.string.companion_install_system_opened,
                        Toast.LENGTH_LONG
                    ).show()
                }

                is CompanionInstaller.Result.Failure -> {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.companion_install_failed)
                        .setMessage(result.message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
