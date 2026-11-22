package moe.shizuku.manager.management

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import kotlinx.coroutines.Job
import moe.shizuku.manager.R
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.utils.AppIconCache
import moe.shizuku.manager.utils.CompanionApp
import moe.shizuku.manager.utils.ShizukuSystemApis
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator
import moe.shizuku.manager.databinding.AppListItemBinding

class AppViewHolder(private val binding: AppListItemBinding) :
    BaseViewHolder<PackageInfo>(binding.root) {

    companion object {
        @JvmField
        val CREATOR = Creator<PackageInfo> { inflater: LayoutInflater, parent: ViewGroup? ->
            AppViewHolder(AppListItemBinding.inflate(inflater, parent, false))
        }
    }

    private val icon get() = binding.icon
    private val name get() = binding.title
    private val pkg get() = binding.summary
    private val switchWidget get() = binding.switchWidget
    private val root get() = binding.requiresRoot

    init {
        itemView.filterTouchesWhenObscured = true
        // The row is a detail affordance; the switch is the only access toggle. This avoids
        // revoking an app by tapping anywhere on its row.
        itemView.setOnClickListener {
            AppDetailSheet.show(itemView.context, data) { uid, granted ->
                (adapter as? AppsAdapter)?.updateGranted(uid, granted)
                (itemView.context.findActivity() as? ApplicationManagementActivity)
                    ?.onAccessChanged(uid, granted)
                refreshRow()
            }
        }
        switchWidget.filterTouchesWhenObscured = true
        switchWidget.isClickable = true
        switchWidget.isFocusable = true
        switchWidget.setOnClickListener { toggleAccess() }
    }

    private inline val packageName get() = data.packageName
    private inline val ai get() = data.applicationInfo!!
    private inline val uid get() = ai.uid

    private var loadIconJob: Job? = null

    private fun toggleAccess() {
        val context = itemView.context
        val current = (adapter as? AppsAdapter)?.isGranted(uid)
            ?: runCatching { AuthorizationManager.granted(packageName, uid) }
                .getOrDefault(false)
        val target = !current

        val error = AccessActions.setGranted(context, packageName, uid, target, detail = "single")
        if (error != null) {
            switchWidget.isChecked = current
            AppAccessUi.showError(context, error)
            return
        }

        (adapter as? AppsAdapter)?.updateGranted(uid, target)
        switchWidget.isChecked = target
        (context.findActivity() as? ApplicationManagementActivity)?.onAccessChanged(uid, target)
        refreshRow()

        if (!target) {
            AppAccessUi.showUndo(context, itemView, packageName, uid) {
                (adapter as? AppsAdapter)?.updateGranted(uid, true)
                switchWidget.isChecked = true
                (context.findActivity() as? ApplicationManagementActivity)?.onAccessChanged(uid, true)
                refreshRow()
            }
        }
    }

    private fun refreshRow() {
        val position = adapterPosition
        if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION || position >= adapter.itemCount) {
            return
        }
        adapter.notifyItemChanged(position, Any())
        if (adapter.itemCount > 0) adapter.notifyItemChanged(0)
    }

    override fun onBind() {
        val pm = itemView.context.packageManager
        val userId = UserHandleCompat.getUserId(uid)
        icon.setImageDrawable(ai.loadIcon(pm))
        name.text = if (userId != UserHandleCompat.myUserId()) {
            val userInfo = ShizukuSystemApis.getUserInfo(userId)
            "${ai.loadLabel(pm)} - ${userInfo.name} ($userId)"
        } else {
            ai.loadLabel(pm)
        }
        pkg.text = ai.packageName
        switchWidget.isChecked = (adapter as? AppsAdapter)?.isGranted(uid) ?: false
        root.visibility =
            if (ai.metaData != null && ai.metaData.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT")) {
                View.VISIBLE
            } else {
                View.GONE
            }

        // V3 clients attach through the Shizuku provider and normally detect Pigu without the
        // package stub. Older clients are the ones that most often hard-check the official
        // package, so only they get the companion hint.
        val modernClient = ai.metaData?.getBoolean("moe.shizuku.client.V3_SUPPORT") == true
        binding.companionNote.isVisible =
            !modernClient && !CompanionApp.isInstalled(context)

        loadIconJob = AppIconCache.loadIconBitmapAsync(context, ai, ai.uid / 100000, icon)
    }

    override fun onBind(payloads: List<Any>) {
        switchWidget.isChecked = (adapter as? AppsAdapter)?.isGranted(uid) ?: false
        binding.requiresRoot.visibility =
            if (ai.metaData != null && ai.metaData.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT")) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    override fun onRecycle() {
        if (loadIconJob?.isActive == true) {
            loadIconJob?.cancel()
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
