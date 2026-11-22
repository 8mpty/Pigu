package moe.shizuku.manager.management

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.SnackbarHelper
import moe.shizuku.manager.ktx.toHtml
import rikka.html.text.HtmlCompat
import rikka.shizuku.Shizuku

object AppAccessUi {

    fun showError(context: Context, error: Throwable) {
        if (error is SecurityException) {
            showAdbLimited(context)
        } else {
            Toast.makeText(
                context,
                error.message ?: context.getString(R.string.app_management_change_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun showAdbLimited(context: Context) {
        val uid = try {
            Shizuku.getUid()
        } catch (tr: Throwable) {
            return
        }
        if (uid == 0) return

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.app_management_dialog_adb_is_limited_title)
            .setMessage(
                context.getString(
                    R.string.app_management_dialog_adb_is_limited_message,
                    Helps.ADB.get(),
                ).toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
            )
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            (it as AlertDialog).findViewById<TextView>(android.R.id.message)
                ?.movementMethod = LinkMovementMethod.getInstance()
        }
        runCatching { dialog.show() }
    }

    fun showUndo(
        context: Context,
        view: View,
        packageName: String,
        uid: Int,
        onUndone: () -> Unit,
    ) {
        SnackbarHelper.show(
            context,
            view,
            context.getString(R.string.app_management_access_revoked, packageName),
            duration = Snackbar.LENGTH_LONG,
            actionText = context.getString(R.string.app_management_undo),
            action = {
                val error = AccessActions.setGranted(
                    context,
                    packageName,
                    uid,
                    granted = true,
                    detail = "undo",
                )
                if (error != null) {
                    showError(context, error)
                } else {
                    onUndone()
                }
            },
        )
    }
}
