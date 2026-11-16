package moe.shizuku.manager.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.adb.AdbSettings
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.receiver.NotifCancelReceiver
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.core.content.asActivity
import rikka.core.util.ClipboardUtils
import rikka.html.text.HtmlCompat

object StartMethods {

    fun startWireless(context: Context, scope: CoroutineScope) {
        if (ShizukuSettings.getAccessPaused()) {
            Toast.makeText(context, R.string.access_pause_start_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        if (ShizukuStateMachine.get() == ShizukuStateMachine.State.STARTING) {
            Toast.makeText(context, context.getString(R.string.toast_shizuku_already_starting), Toast.LENGTH_SHORT).show()
            return
        }

        context.sendBroadcast(Intent(context, NotifCancelReceiver::class.java))

        val cr = context.contentResolver
        AdbSettings.enableDebugging(context)

        val adbEnabled = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0)
        val tcpPort = EnvironmentUtils.getAdbTcpPort()
        val tcpMode = ShizukuSettings.getTcpMode()

        when (StartRoutePolicy.decide(adbEnabled, tcpPort, EnvironmentUtils.isTlsSupported(), tcpMode)) {
            StartRoute.USB_DEBUGGING_OFF -> {
                if (AdbSettings.isDebuggingSettingsRestricted(context)) {
                    SettingsHelper.showDebuggingSettingsRestrictedDialog(context)
                } else {
                    WadbEnableUsbDebuggingDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
                }
            }
            StartRoute.WIRELESS_DEBUGGING_UNAVAILABLE -> {
                WadbNotEnabledDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
            }
            StartRoute.DISCOVER -> {
                startWirelessDiscovery(context)
            }
            StartRoute.STOP_TCP_AND_DISCOVER -> {
                scope.launch {
                    AdbStarter.stopTcp(context, tcpPort)
                    startWirelessDiscovery(context)
                }
            }
            StartRoute.DIRECT_PORT -> {
                val intent = Intent(context, StarterActivity::class.java).apply {
                    putExtra(StarterActivity.EXTRA_PORT, tcpPort)
                }
                context.startActivity(intent)
            }
        }
    }

    private fun startWirelessDiscovery(context: Context) {
        AdbSettings.setWirelessDebuggingEnabled(context, true)
        context.startActivity(
            Intent(context, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_DISCOVER_WIRELESS, true)
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun pairWireless(context: Context) {
        if (EnvironmentUtils.isTelevision()) {
            context.showAccessibilityDialog()
        } else if ((context.display?.displayId ?: -1) > 0 || ShizukuSettings.getLegacyPairing()) {
            // Running in a multi-display environment (e.g., Windows Subsystem for Android),
            // pairing dialog can be displayed simultaneously with Shizuku.
            // Input from notification is harder to use under this situation.
            AdbPairDialogFragment().show(context.asActivity<FragmentActivity>().supportFragmentManager)
        } else {
            context.startActivity(Intent(context, AdbPairingTutorialActivity::class.java))
        }
    }

    fun showAdbCommandDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.home_adb_button_view_command)
            .setMessage(
                HtmlCompat.fromHtml(
                    context.getString(
                        R.string.home_adb_dialog_view_command_message,
                        Starter.adbCommand
                    )
                )
            )
            .setPositiveButton(R.string.home_adb_dialog_view_command_copy_button) { _, _ ->
                if (ClipboardUtils.put(context, Starter.adbCommand)) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_copied_to_clipboard),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.home_adb_dialog_view_command_button_send) { _, _ ->
                var intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, Starter.adbCommand)
                intent = Intent.createChooser(
                    intent,
                    context.getString(R.string.home_adb_dialog_view_command_button_send)
                )
                context.startActivity(intent)
            }
            .show()
    }

    fun startRoot(context: Context) {
        if (ShizukuSettings.getAccessPaused()) {
            Toast.makeText(context, R.string.access_pause_start_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(
            Intent(context, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, true)
            }
        )
    }
}
