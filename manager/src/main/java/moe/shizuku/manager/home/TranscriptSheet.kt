package moe.shizuku.manager.home

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.DialogTranscriptBinding
import rikka.core.util.ClipboardUtils

object TranscriptSheet {

    fun show(context: Context, transcript: String) {
        val binding = DialogTranscriptBinding.inflate(android.view.LayoutInflater.from(context))
        val sheet = BottomSheetDialog(context).apply {
            setContentView(binding.root)
            behavior.skipCollapsed = true
        }

        binding.content.text = transcript.ifBlank {
            context.getString(R.string.service_doctor_no_transcript)
        }
        binding.copy.setOnClickListener { view ->
            if (ClipboardUtils.put(view.context, binding.content.text.toString()) &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
            ) {
                Toast.makeText(view.context, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT)
                    .show()
            }
        }
        binding.close.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }
}
