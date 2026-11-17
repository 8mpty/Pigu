package moe.shizuku.manager.management

import android.content.pm.PackageInfo
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.audit.AuditLog
import moe.shizuku.manager.databinding.AppsActivityBinding
import moe.shizuku.manager.utils.CompanionApp
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.lifecycle.Status
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.fixEdgeEffect
import java.util.Locale

class ApplicationManagementActivity : AppBarActivity() {

    private val viewModel: AppsViewModel by viewModels()
    private val adapter = AppsAdapter()

    private lateinit var binding: AppsActivityBinding

    private var allPackages: List<PackageInfo> = emptyList()
    private var grantedUids: Set<Int> = emptySet()
    private var labels: Map<String, String> = emptyMap()
    private var decisions: Map<String, Boolean> = emptyMap()
    private var recentTimes: Map<String, Long> = emptyMap()
    private var companionInstalled = false

    private var query: String = ""
    private var filter = AppFilterPolicy.Filter.ALL
    private var sort = AppFilterPolicy.Sort.NAME

    private val stateListener: (ShizukuStateMachine.State) -> Unit = {
        if (ShizukuStateMachine.isDead() && !isFinishing) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ShizukuStateMachine.isRunning()) {
            finish()
            return
        }

        binding = AppsActivityBinding.inflate(layoutInflater, rootView, true)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.home_app_management_title)

        viewModel.packages.observe(this) {
            when (it.status) {
                Status.SUCCESS -> {
                    allPackages = it.data ?: return@observe
                    loadMetadata()
                }
                Status.ERROR -> {
                    finish()
                    val tr = it.error
                    Toast.makeText(this, tr?.toString() ?: "unknown", Toast.LENGTH_SHORT).show()
                    tr?.printStackTrace()
                }
                Status.LOADING -> {}
            }
        }
        viewModel.grantedUids.observe(this) {
            if (it.status == Status.SUCCESS) {
                grantedUids = it.data ?: emptySet()
                applyFilter()
            }
        }
        viewModel.load()

        binding.filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            filter = when (checkedIds.firstOrNull()) {
                R.id.chip_allowed -> AppFilterPolicy.Filter.ALLOWED
                R.id.chip_denied -> AppFilterPolicy.Filter.DENIED
                R.id.chip_companion -> AppFilterPolicy.Filter.COMPANION
                else -> AppFilterPolicy.Filter.ALL
            }
            applyFilter()
        }

        val recyclerView = binding.list
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()
        recyclerView.addEdgeSpacing(top = 8f, bottom = 8f, unit = TypedValue.COMPLEX_UNIT_DIP)

        ShizukuStateMachine.addListener(stateListener)
    }

    private fun loadMetadata() {
        val packages = allPackages
        val pm = packageManager
        lifecycleScope.launch {
            val computedLabels = withContext(Dispatchers.IO) {
                packages.associate { pi ->
                    pi.packageName to runCatching {
                        pi.applicationInfo?.loadLabel(pm)?.toString()
                    }.getOrNull().orEmpty().ifEmpty { pi.packageName }
                }
            }
            val computedDecisions = withContext(Dispatchers.IO) {
                AuditLog.lastDecisions(this@ApplicationManagementActivity)
            }
            val computedRecent = withContext(Dispatchers.IO) {
                val map = HashMap<String, Long>()
                for (entry in AuditLog.entries(this@ApplicationManagementActivity)) {
                    val key = if (entry.packageName != null && entry.uid != null) {
                        "${entry.packageName}#${entry.uid}"
                    } else {
                        entry.packageName ?: "uid:${entry.uid ?: -1}"
                    }
                    if (!map.containsKey(key)) map[key] = entry.timestamp
                }
                map
            }
            companionInstalled = withContext(Dispatchers.IO) {
                CompanionApp.isInstalled(this@ApplicationManagementActivity)
            }

            labels = computedLabels
            decisions = computedDecisions
            recentTimes = computedRecent
            applyFilter()
        }
    }

    private fun applyFilter() {
        val now = allPackages.filter { pi ->
            val ai = pi.applicationInfo ?: return@filter false
            val uid = ai.uid
            val granted = grantedUids.contains(uid)
            val denied = !granted &&
                AuditLog.decisionFor(decisions, pi.packageName, uid) == false
            val modern = ai.metaData?.getBoolean("moe.shizuku.client.V3_SUPPORT") == true
            val needsCompanion = !modern && !companionInstalled
            val matchesQuery = query.isBlank() ||
                labels[pi.packageName]?.lowercase(Locale.getDefault())?.contains(query.lowercase(Locale.getDefault())) == true ||
                pi.packageName.lowercase(Locale.getDefault()).contains(query.lowercase(Locale.getDefault()))

            matchesQuery && AppFilterPolicy.matches(filter, granted, denied, needsCompanion)
        }

        val sorted = when (sort) {
            AppFilterPolicy.Sort.NAME -> now.sortedBy {
                labels[it.packageName]?.lowercase(Locale.getDefault()) ?: it.packageName
            }
            AppFilterPolicy.Sort.RECENT -> now.sortedWith(
                compareByDescending<PackageInfo> {
                    val uid = it.applicationInfo?.uid ?: -1
                    recentTimes["${it.packageName}#$uid"] ?: 0L
                }.thenBy {
                    labels[it.packageName]?.lowercase(Locale.getDefault()) ?: it.packageName
                }
            )
        }

        adapter.updateData(sorted, grantedUids)
    }

    fun onAccessChanged(uid: Int, granted: Boolean) {
        grantedUids = if (granted) grantedUids + uid else grantedUids - uid
        adapter.updateGranted(uid, granted)
        applyFilter()
    }

    fun onBulkAccessChanged(uids: Collection<Int>, granted: Boolean) {
        grantedUids = if (granted) grantedUids + uids else grantedUids - uids.toSet()
        applyFilter()
    }

    fun reloadApps() {
        viewModel.load()
        loadMetadata()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    override fun onDestroy() {
        ShizukuStateMachine.removeListener(stateListener)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_management, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = SearchView(this).apply {
            queryHint = getString(R.string.app_management_search_hint)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(text: String?): Boolean {
                    this@ApplicationManagementActivity.query = text.orEmpty()
                    applyFilter()
                    return true
                }

                override fun onQueryTextChange(text: String?): Boolean {
                    this@ApplicationManagementActivity.query = text.orEmpty()
                    applyFilter()
                    return true
                }
            })
        }
        searchItem?.actionView = searchView
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_companion_info -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.app_management_companion_info_title)
                    .setMessage(R.string.app_management_companion_info_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.app_management_sort_name),
            getString(R.string.app_management_sort_recent),
        )
        val selected = if (sort == AppFilterPolicy.Sort.NAME) 0 else 1
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_management_sort_title)
            .setSingleChoiceItems(options, selected) { dialog, which ->
                sort = if (which == 0) AppFilterPolicy.Sort.NAME else AppFilterPolicy.Sort.RECENT
                dialog.dismiss()
                applyFilter()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
