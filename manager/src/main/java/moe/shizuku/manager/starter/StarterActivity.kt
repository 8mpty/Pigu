package moe.shizuku.manager.starter

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLProtocolException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import moe.shizuku.manager.AppConstants.EXTRA
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbSettings
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.utils.LocalNetworkPermission
import moe.shizuku.manager.utils.RootWarning
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.starter.StarterTranscript
import moe.shizuku.manager.databinding.StarterActivityBinding
import rikka.core.util.ClipboardUtils
import rikka.lifecycle.Resource
import rikka.lifecycle.Status

private class NotRootedException: Exception()

private const val DISCOVERY_TIMEOUT_MS = 15_000L

class StarterActivity : AppBarActivity() {

    private val viewModel: ViewModel by viewModels()

    private lateinit var binding: StarterActivityBinding
    private var localTcpPort = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_24)

        binding = StarterActivityBinding.inflate(layoutInflater, rootView, true)

        binding.copy.setOnClickListener {
            if (ClipboardUtils.put(this, binding.text1.text)) {
                Toast.makeText(this, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.output.observe(this) {
            val output = it.data!!.trim()
            if (it.status == Status.ERROR) {
                var message = 0
                when (it.error) {
                    is AdbKeyException -> {
                        message = R.string.adb_error_key_store
                    }
                    is NotRootedException -> {
                        message = R.string.start_with_root_failed
                    }
                    is SocketTimeoutException -> {
                        message = R.string.cannot_connect_port
                    }
                    is ConnectException -> {
                        message = R.string.cannot_connect_port
                    }
                    is SSLProtocolException -> {
                        message = R.string.adb_pair_required
                    }
                }

                val messageText: CharSequence? = when {
                    localTcpPort > 0 &&
                        (it.error is ConnectException || it.error is SocketTimeoutException) ->
                        getString(R.string.local_tcp_connect_failed, localTcpPort)
                    message != 0 -> getString(message)
                    else -> null
                }
                if (messageText != null) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(messageText)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            binding.text1.text = output
        }
    }

    private var hasStarted = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasStarted) {
            hasStarted = true
            val root = intent.getBooleanExtra(EXTRA_IS_ROOT, false)
            if (root && !RootWarning.isAcknowledged()) {
                Toast.makeText(this, R.string.root_warning_background_blocked, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            val port = intent.getIntExtra(EXTRA_PORT, 0)
            val discoverWireless = intent.getBooleanExtra(EXTRA_DISCOVER_WIRELESS, false)
            val localTcp = intent.getBooleanExtra(EXTRA_LOCAL_TCP, false)
            localTcpPort = if (localTcp) port else 0

            ShizukuSettings.setLastLaunchMode(
                when {
                    root -> ShizukuSettings.LaunchMethod.ROOT
                    port != 0 || discoverWireless -> ShizukuSettings.LaunchMethod.WIRELESS_ADB
                    else -> ShizukuSettings.LaunchMethod.ADB
                }
            )
            // A start from the UI is an explicit request, so it clears a previous deliberate
            // stop before the watchdog is allowed to treat the service as crashed.
            ShizukuSettings.setManuallyStopped(false)
            startService(root, port, discoverWireless, localTcp)
        }
    }

    private fun startService(root: Boolean, port: Int, discoverWireless: Boolean, localTcp: Boolean) {
        if (!discoverWireless || LocalNetworkPermission.isGranted(this)) {
            viewModel.start(root, port, discoverWireless, localTcp)
            return
        }

        LocalNetworkPermission.request(this) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    R.string.local_network_permission_denied,
                    Toast.LENGTH_LONG,
                ).show()
                finish()
                return@request
            }
            if (!isFinishing) viewModel.start(root, port, discoverWireless, localTcp)
        }
    }

    companion object {

        const val EXTRA_IS_ROOT = "$EXTRA.IS_ROOT"
        const val EXTRA_PORT = "$EXTRA.PORT"
        const val EXTRA_DISCOVER_WIRELESS = "$EXTRA.DISCOVER_WIRELESS"
        const val EXTRA_LOCAL_TCP = "$EXTRA.LOCAL_TCP"
    }
}

class ViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext

    private val sb = StringBuilder()
    private val _output = MutableLiveData<Resource<StringBuilder>>()

    val output = _output as LiveData<Resource<StringBuilder>>

    private val handler = CoroutineExceptionHandler { _, throwable ->
        ShizukuStateMachine.update()
        log(error = throwable)
        StarterTranscript.finish(
            appContext,
            success = false,
            message = throwable.message ?: throwable.javaClass.simpleName,
        )
    }

    private var started = false

    fun start(root: Boolean, port: Int, discoverWireless: Boolean, localTcp: Boolean = false) {
        if (started) return
        started = true
        StarterTranscript.begin(appContext)

        viewModelScope.launch(handler) {
            if (root) {
                startRoot()
                Starter.waitForBinder { log(it) }
            } else {
                var attemptedAdb = false
                try {
                    val actualPort =
                        if (discoverWireless && port == 0) discoverWirelessPort() else port
                    attemptedAdb = true
                    AdbStarter.startAdb(
                        appContext,
                        actualPort,
                        switchToTcpMode = !localTcp,
                    ) { log(it) }
                    Starter.waitForBinder { log(it) }
                    AdbStarter.finishWirelessStart(appContext, success = true)
                } catch (e: Throwable) {
                    // A discovery failure did not touch wireless debugging; leave it on
                    // so the user can retry without pairing again.
                    if (attemptedAdb) {
                        AdbStarter.finishWirelessStart(appContext, success = false)
                    }
                    throw e
                }
            }
            StarterTranscript.finish(
                appContext,
                success = true,
                message = Starter.serviceStartedMessage,
            )
        }
    }

    /**
     * Finds the port adbd advertises for wireless debugging. The Starter screen is
     * started straight after pairing, so this is the step that used to live in the
     * separate discovery dialog.
     */
    private suspend fun discoverWirelessPort(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw SocketTimeoutException("Wireless debugging requires Android 11")
        }

        log("Searching for wireless debugging...\n")
        if (!AdbSettings.setWirelessDebuggingEnabled(appContext, true)) {
            log("Could not enable wireless debugging automatically.\n")
        }

        val port = try {
            withTimeout(DISCOVERY_TIMEOUT_MS) {
                val found = CompletableDeferred<Int>()
                val mdns = AdbMdns(appContext, AdbMdns.TLS_CONNECT) { pair ->
                    val discovered = pair.second
                    if (discovered in 1..65535) found.complete(discovered)
                }
                try {
                    mdns.start()
                    found.await()
                } finally {
                    mdns.stop()
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw SocketTimeoutException("Timed out searching for wireless debugging")
        }

        log("Found wireless debugging port: $port\n")
        return port
    }

    private fun log(line: String? = null, error: Throwable? = null) {
        line?.let {
            sb.appendLine(it)
            StarterTranscript.append(appContext, it)
        }
        error?.let {
            sb.appendLine().appendLine(Log.getStackTraceString(it))
            StarterTranscript.append(appContext, Log.getStackTraceString(it))
        }

        if (error == null) _output.postValue(Resource.success(sb))
        else _output.postValue(Resource.error(error, sb))
    }

    private suspend fun startRoot() {
        log("Starting with root...\n")

        return withContext(Dispatchers.IO) {
            if (!Shell.getShell().isRoot) {
                // Try again just in case
                Shell.getCachedShell()?.close()

                if (!Shell.getShell().isRoot) {
                    Shell.getCachedShell()?.close()
                    throw NotRootedException()
                }
            }

            ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
            suspendCancellableCoroutine { cont ->
                Shell.cmd(Starter.internalCommand)
                    .to(object : CallbackList<String?>() {
                        override fun onAddElement(s: String?) { s?.let { log(it) } }
                    })
                    .submit {
                        if (it.isSuccess) {
                            cont.resume(Unit)
                        } else {
                            cont.resumeWithException(Exception("Failed to start with root"))
                        }
                    }
            }
        }
    }
    
}
