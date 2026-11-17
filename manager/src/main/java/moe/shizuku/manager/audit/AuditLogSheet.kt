package moe.shizuku.manager.audit

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.DialogAuditLogBinding
import rikka.core.util.ClipboardUtils

object AuditLogSheet {

    fun show(context: Context, onChanged: () -> Unit) {
        val binding = DialogAuditLogBinding.inflate(android.view.LayoutInflater.from(context))
        val sheet = BottomSheetDialog(context).apply {
            setContentView(binding.root)
            behavior.skipCollapsed = true
        }

        fun render() {
            val entries = AuditLog.entries(context)
            binding.summary.text = context.resources.getQuantityString(
                R.plurals.audit_log_summary_count,
                entries.size,
                entries.size,
            )
            binding.entries.text = if (entries.isEmpty()) {
                context.getString(R.string.audit_log_empty)
            } else {
                context.getString(R.string.audit_log_copy_header) + "\n\n" +
                    AuditLog.format(context, entries)
            }
            binding.clear.isEnabled = entries.isNotEmpty()
        }

        binding.copy.setOnClickListener { view ->
            val text = binding.entries.text.toString()
            if (ClipboardUtils.put(view.context, text) &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
            ) {
                Toast.makeText(view.context, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT)
                    .show()
            }
        }

        binding.clear.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.audit_log_clear_title)
                .setMessage(R.string.audit_log_clear_message)
                .setPositiveButton(R.string.audit_log_clear) { _, _ ->
                    AuditLog.clear(context)
                    render()
                    onChanged()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.close.setOnClickListener { sheet.dismiss() }

        render()
        sheet.show()
    }
}
