package moe.shizuku.manager.management

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import moe.shizuku.manager.R
import moe.shizuku.manager.audit.AuditLog
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.databinding.DialogAppDetailBinding
import moe.shizuku.manager.utils.CompanionApp
import moe.shizuku.manager.utils.ShizukuSystemApis
import moe.shizuku.manager.utils.UserHandleCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detail sheet shared by the App Management row click and the switch long-press path.
 * The switch here is the same explicit grant/revoke action as the row switch, not a separate
 * permission model.
 */
object AppDetailSheet {

    fun show(
        context: Context,
        pi: PackageInfo,
        onAccessChanged: (uid: Int, granted: Boolean) -> Unit,
    ) {
        val ai = pi.applicationInfo ?: return
        val uid = ai.uid
        val packageName = pi.packageName
        val pm = context.packageManager

        val binding = DialogAppDetailBinding.inflate(
            android.view.LayoutInflater.from(context)
        )
        val sheet = BottomSheetDialog(context).apply {
            setContentView(binding.root)
            behavior.skipCollapsed = true
        }

        binding.icon.setImageDrawable(
            runCatching { ai.loadIcon(pm) }.getOrNull()
        )
        binding.title.text = runCatching { ai.loadLabel(pm) }.getOrNull() ?: packageName
        binding.packageName.text = packageName

        val userId = UserHandleCompat.getUserId(uid)
        val userLabel = runCatching {
            ShizukuSystemApis.getUserInfo(userId).name
        }.getOrNull()
        binding.uid.text = context.getString(
            R.string.app_management_detail_uid,
            uid,
            userId,
            if (userLabel.isNullOrBlank()) "" else " · $userLabel",
        )

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
        binding.version.text = context.getString(
            R.string.app_management_detail_version,
            pi.versionName ?: "?",
            versionCode,
        )

        val modern = ai.metaData?.getBoolean("moe.shizuku.client.V3_SUPPORT") == true
        val requiresRoot = ai.metaData?.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT") == true
        binding.clientType.text = context.getString(
            if (modern) R.string.app_management_detail_client_modern
            else R.string.app_management_detail_client_legacy
        ) + if (requiresRoot) {
            " · " + context.getString(R.string.app_management_item_summary_requires_root)
        } else {
            ""
        }

        binding.companionNote.visibility =
            if (!modern && !CompanionApp.isInstalled(context)) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

        var granted = runCatching {
            AuthorizationManager.granted(packageName, uid)
        }.getOrDefault(false)

        fun refreshLastRequest() {
            val entry = AuditLog.lastFor(context, packageName, uid)
            binding.lastRequest.text = if (entry == null) {
                context.getString(R.string.app_management_detail_no_request)
            } else {
                context.getString(
                    R.string.app_management_detail_last_request,
                    AuditLog.actionLabel(entry.action) + " · " + AuditLog.resultLabel(entry.result),
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        .format(Date(entry.timestamp)),
                )
            }
        }

        binding.accessSwitch.isChecked = granted
        binding.accessSwitch.setOnClickListener {
            val target = !granted
            val error = AccessActions.setGranted(
                context,
                packageName,
                uid,
                target,
                detail = "detail",
            )
            if (error != null) {
                binding.accessSwitch.isChecked = granted
                AppAccessUi.showError(context, error)
                return@setOnClickListener
            }

            granted = target
            binding.accessSwitch.isChecked = target
            onAccessChanged(uid, target)
            refreshLastRequest()

            if (!target) {
                AppAccessUi.showUndo(context, binding.root, packageName, uid) {
                    granted = true
                    binding.accessSwitch.isChecked = true
                    onAccessChanged(uid, true)
                    refreshLastRequest()
                }
            }
        }

        binding.openApp.setOnClickListener {
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                Toast.makeText(context, R.string.app_management_open_app_failed, Toast.LENGTH_SHORT)
                    .show()
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        Toast.makeText(
                            context,
                            R.string.app_management_open_app_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
            }
        }

        binding.appInfo.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure {
                    Toast.makeText(
                        context,
                        R.string.app_management_open_app_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        }

        refreshLastRequest()
        sheet.show()
    }
}
