package moe.shizuku.manager.home

import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.ActivityStartMethodsBinding
import moe.shizuku.manager.databinding.DialogLocalTcpBinding
import moe.shizuku.manager.utils.EnvironmentUtils

class StartMethodsActivity : AppBarActivity() {

    private lateinit var binding: ActivityStartMethodsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStartMethodsBinding.inflate(layoutInflater, rootView, true)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.start_methods_title)

        binding.wirelessPair.isVisible = EnvironmentUtils.isTlsSupported()
        binding.wirelessPair.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                StartMethods.pairWireless(this)
            }
        }
        binding.wirelessStart.setOnClickListener {
            StartMethods.startWireless(this, lifecycleScope)
        }
        val scroll = binding.root
        val baseLeft = scroll.paddingLeft
        val baseRight = scroll.paddingRight
        val baseBottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = baseLeft + bars.left,
                right = baseRight + bars.right,
                bottom = baseBottom + bars.bottom,
            )
            insets
        }

        binding.localTcpStart.setOnClickListener {
            showLocalTcpDialog()
        }
        binding.adbButton.setOnClickListener {
            StartMethods.showAdbCommandDialog(this)
        }

        val rooted = EnvironmentUtils.isRooted()
        binding.rootCard.apply {
            isEnabled = rooted
            alpha = if (rooted) 1f else 0.5f
        }
        binding.rootStart.isEnabled = rooted
        if (!rooted) {
            binding.rootSummary.setText(R.string.start_root_unavailable)
        }
        binding.rootStart.setOnClickListener {
            if (rooted) StartMethods.startRoot(this)
        }
    }

    private fun showLocalTcpDialog() {
        val dialogBinding = DialogLocalTcpBinding.inflate(layoutInflater)
        val defaultPort = ShizukuSettings.getTcpPort()
        dialogBinding.port.setText(defaultPort.toString())
        dialogBinding.port.setSelection(0, dialogBinding.port.text?.length ?: 0)
        dialogBinding.port.doAfterTextChanged { dialogBinding.portLayout.error = null }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_local_tcp_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_local_tcp_start, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        fun startFromPort() {
            val port = dialogBinding.port.text?.toString()?.toIntOrNull()
            if (port == null || port !in 1..65535) {
                dialogBinding.portLayout.error = getString(R.string.dialog_local_tcp_invalid)
                dialogBinding.port.requestFocus()
                return
            }

            dialogBinding.portLayout.error = null
            dialog.dismiss()
            StartMethods.startLocalTcp(this, port)
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { startFromPort() }
            dialogBinding.port.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    startFromPort()
                    true
                } else {
                    false
                }
            }
            dialogBinding.port.requestFocus()
        }
        dialog.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
