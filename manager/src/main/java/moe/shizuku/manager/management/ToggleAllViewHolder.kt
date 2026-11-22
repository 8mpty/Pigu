package moe.shizuku.manager.management

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.AppListToggleAllBinding
import moe.shizuku.manager.management.AppsAdapter.HeaderMarker
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class ToggleAllViewHolder(private val binding: AppListToggleAllBinding) :
    BaseViewHolder<HeaderMarker>(binding.root), View.OnClickListener {

    companion object {
        @JvmField
        val CREATOR = Creator<HeaderMarker> { inflater: LayoutInflater, parent: ViewGroup? ->
            ToggleAllViewHolder(AppListToggleAllBinding.inflate(inflater, parent, false))
        }
    }

    private val switchWidget get() = binding.switchWidget
    private val appsAdapter get() = adapter as? AppsAdapter

    init {
        itemView.filterTouchesWhenObscured = true
        itemView.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        val adapter = appsAdapter ?: return
        val enable = !adapter.allGranted()
        val count = adapter.appCount()
        if (count == 0) return

        val title = if (enable) {
            R.string.app_management_grant_all_title
        } else {
            R.string.app_management_revoke_all_title
        }
        val message = v.context.resources.getQuantityString(
            if (enable) R.plurals.app_management_grant_all_message
            else R.plurals.app_management_revoke_all_message,
            count,
            count,
        )

        MaterialAlertDialogBuilder(v.context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(if (enable) R.string.app_management_grant_all_action else R.string.app_management_revoke_all_action) { _, _ ->
                applyAll(enable)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyAll(enable: Boolean) {
        val adapter = appsAdapter ?: return
        val context = itemView.context
        val targets = adapter.apps().mapNotNull { pi ->
            pi.applicationInfo?.let { ai -> pi.packageName to ai.uid }
        }
        if (targets.isEmpty()) return

        val progress = MaterialAlertDialogBuilder(context)
            .setMessage(R.string.app_management_bulk_applying)
            .setCancelable(false)
            .show()

        val lifecycleOwner = itemView.findViewTreeLifecycleOwner()
        if (lifecycleOwner == null) {
            progress.dismiss()
            return
        }

        lifecycleOwner.lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    AccessActions.setAll(context, targets, enable)
                }
            } catch (tr: Throwable) {
                progress.dismiss()
                Toast.makeText(
                    context,
                    context.getString(R.string.app_management_change_failed),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }

            for ((_, uid) in targets) {
                if (result.failed == 0) {
                    adapter.updateGranted(uid, enable)
                }
            }
            // If any call failed, reloading the real state is the only honest option.
            if (result.failed > 0) {
                (context.findActivity() as? ApplicationManagementActivity)?.let {
                    it.reloadApps()
                }
            } else {
                (context.findActivity() as? ApplicationManagementActivity)
                    ?.onBulkAccessChanged(targets.map { it.second }, enable)
            }

            adapter.notifyDataSetChanged()
            switchWidget.isChecked = adapter.allGranted()
            progress.dismiss()

            val message = context.resources.getQuantityString(
                if (enable) R.plurals.app_management_bulk_granted
                else R.plurals.app_management_bulk_revoked,
                result.succeeded,
                result.succeeded,
            )
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBind() {
        switchWidget.isChecked = appsAdapter?.allGranted() ?: false
    }

    override fun onBind(payloads: List<Any>) {
        switchWidget.isChecked = appsAdapter?.allGranted() ?: false
    }

    override fun onRecycle() {}

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
