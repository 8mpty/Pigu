package moe.shizuku.manager.authorization

import android.app.Dialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.audit.AuditLog
import moe.shizuku.manager.databinding.ConfirmationDialogBinding
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.utils.Logger.LOGGER
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.core.res.resolveColor
import rikka.html.text.HtmlCompat
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME

class RequestPermissionActivity : AppActivity() {

    private lateinit var dialog: Dialog
    private lateinit var binding: ConfirmationDialogBinding
    private val handler = Handler(Looper.getMainLooper())

    private var answered = false
    private var auditShown = false
    private var auditPackage: String? = null
    private var auditUid: Int = -1

    private val enableAnswerButtons = Runnable {
        if (!isFinishing && !isDestroyed && ::binding.isInitialized) {
            binding.button1.isEnabled = true
            binding.button3.isEnabled = true
            binding.button1.alpha = 1f
            binding.button3.alpha = 1f
        }
    }

    private fun setResult(requestUid: Int, requestPid: Int, requestCode: Int, allowed: Boolean, onetime: Boolean) {
        val data = Bundle()
        data.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        data.putBoolean(REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        try {
            Shizuku.dispatchPermissionConfirmationResult(requestUid, requestPid, requestCode, data)
        } catch (e: Throwable) {
            LOGGER.e("dispatchPermissionConfirmationResult")
        }
    }

    private fun checkSelfPermission(): Boolean {
        val permission = Shizuku.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED
        if (permission) return true

        val icon = getDrawable(R.drawable.ic_system_icon)
        icon?.setTint(theme.resolveColor(android.R.attr.colorAccent))

        val dialog = MaterialAlertDialogBuilder(this)
                .setIcon(icon)
                .setTitle("Shizuku: ${getString(R.string.app_management_dialog_adb_is_limited_title)}")
                .setMessage(getString(R.string.app_management_dialog_adb_is_limited_message, Helps.ADB.get()).toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE))
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener { finish() }
                .create()
        dialog.setOnShowListener {
            (it as AlertDialog).findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
        }
        try {
            dialog.show()
        } catch (ignored: Throwable) {
            finish()
        }
        return false
    }

    private suspend fun waitForBinder(): Boolean {
        return try {
            withTimeout(5000) {
                ShizukuStateMachine.asFlow().first { it == ShizukuStateMachine.State.RUNNING }
            }
            true
        } catch (e: TimeoutCancellationException) {
            LOGGER.e(e, "Binder not received in 5s")
            false
        }
    }

    private fun applicationInfo(): ApplicationInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("applicationInfo", ApplicationInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("applicationInfo")
        }
    }

    private fun answer(uid: Int, pid: Int, requestCode: Int, allowed: Boolean, onetime: Boolean) {
        if (answered) return
        answered = true
        AuditLog.record(
            this,
            AuditLog.Action.PERMISSION,
            if (allowed) AuditLog.Result.ALLOWED else AuditLog.Result.DENIED,
            auditPackage,
            auditUid.takeIf { it > 0 } ?: uid,
            detail = "prompt",
        )
        setResult(uid, pid, requestCode, allowed, onetime)
        dialog.dismiss()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFinishOnTouchOutside(false)
        window.decorView.filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { window.setHideOverlayWindows(true) }
        }

        val uid = intent.getIntExtra("uid", -1)
        val pid = intent.getIntExtra("pid", -1)
        val requestCode = intent.getIntExtra("requestCode", -1)
        val ai = applicationInfo()
        val packages = if (uid > 0) {
            runCatching { packageManager.getPackagesForUid(uid)?.toList() }.getOrNull()
        } else {
            null
        }
        val rawLabel = try {
            ai?.loadLabel(packageManager)
        } catch (e: Exception) {
            null
        }

        val decision = PermissionRequestPolicy.evaluate(
            uid = uid,
            pid = pid,
            packageName = ai?.packageName,
            applicationUid = ai?.uid ?: -1,
            packagesForUid = packages,
            rawLabel = rawLabel,
            fallbackLabel = ai?.packageName,
        )

        auditUid = uid
        auditPackage = decision.packageName.takeIf { it.isNotEmpty() } ?: ai?.packageName

        if (!decision.accepted) {
            LOGGER.w("Permission request rejected: %s", decision.rejection)
            AuditLog.record(
                this@RequestPermissionActivity,
                AuditLog.Action.PERMISSION,
                AuditLog.Result.DENIED,
                auditPackage,
                uid.takeIf { it > 0 },
                detail = "rejected: ${decision.rejection}",
            )
            if (uid > 0 && pid > 0 && requestCode >= 0) {
                setResult(uid, pid, requestCode, allowed = false, onetime = true)
            }
            finish()
            return
        }

        // Do not block onCreate while waiting for a late binder; that can ANR before the
        // permission prompt is even visible.
        lifecycleScope.launch {
            if (!waitForBinder()) {
                AuditLog.record(
                    this@RequestPermissionActivity,
                    AuditLog.Action.PERMISSION,
                    AuditLog.Result.DENIED,
                    auditPackage,
                    uid.takeIf { it > 0 },
                    detail = "server unavailable",
                )
                finish()
                return@launch
            }

            if (!checkSelfPermission()) {
                AuditLog.record(
                    this@RequestPermissionActivity,
                    AuditLog.Action.PERMISSION,
                    AuditLog.Result.DENIED,
                    auditPackage,
                    uid.takeIf { it > 0 },
                    detail = "manager cannot grant runtime permissions",
                )
                setResult(uid, pid, requestCode, allowed = false, onetime = true)
                return@launch
            }

            binding = ConfirmationDialogBinding.inflate(layoutInflater).apply {
                title.text = HtmlCompat.fromHtml(
                    getString(
                        R.string.permission_warning_template,
                        TextUtils.htmlEncode(decision.label),
                        getString(R.string.permission_group_description),
                    ),
                )
                packageName.text = decision.packageName

                if (decision.sharedUidPackages.isEmpty()) {
                    sharedUidWarning.visibility = android.view.View.GONE
                } else {
                    sharedUidWarning.visibility = android.view.View.VISIBLE
                    sharedUidWarning.text = getString(
                        R.string.permission_shared_uid_warning,
                        decision.sharedUidPackages.joinToString(", "),
                    )
                }

                // A tap that was already travelling when the prompt appeared must not answer it.
                button1.isEnabled = false
                button3.isEnabled = false
                button1.alpha = 0.5f
                button3.alpha = 0.5f
                button1.setOnClickListener {
                    answer(uid, pid, requestCode, allowed = true, onetime = false)
                }
                button3.setOnClickListener {
                    answer(uid, pid, requestCode, allowed = false, onetime = true)
                }
            }

            dialog = MaterialAlertDialogBuilder(this@RequestPermissionActivity)
                    .setView(binding.root)
                    .setCancelable(false)
                    .setOnDismissListener { finish() }
                    .create()
            dialog.setCanceledOnTouchOutside(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Set this before the dialog window is added so the window manager applies it.
                runCatching { dialog.window?.setHideOverlayWindows(true) }
            }
            dialog.setOnShowListener {
                dialog.window?.decorView?.filterTouchesWhenObscured = true
                if (!auditShown) {
                    auditShown = true
                    AuditLog.record(
                        this@RequestPermissionActivity,
                        AuditLog.Action.PERMISSION,
                        AuditLog.Result.SHOWN,
                        auditPackage,
                        auditUid.takeIf { it > 0 },
                        detail = "prompt",
                    )
                }
                handler.postDelayed(enableAnswerButtons, TAP_GRACE_MS)
            }
            try {
                dialog.show()
            } catch (e: Throwable) {
                LOGGER.e(e, "show permission dialog")
                AuditLog.record(
                    this@RequestPermissionActivity,
                    AuditLog.Action.PERMISSION,
                    AuditLog.Result.DENIED,
                    auditPackage,
                    auditUid.takeIf { it > 0 },
                    detail = "dialog failed",
                )
                setResult(uid, pid, requestCode, allowed = false, onetime = true)
                finish()
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.flags and OBSCURED_MASK != 0) return true
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        handler.removeCallbacks(enableAnswerButtons)
        super.onDestroy()
    }

    private companion object {
        const val TAP_GRACE_MS = 500L
        const val OBSCURED_MASK =
            MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
    }
}
