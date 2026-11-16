package moe.shizuku.manager.home

import android.os.Bundle
import androidx.activity.viewModels
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.HomeActivityBinding
import moe.shizuku.manager.management.AppDiscoverySummary
import moe.shizuku.manager.management.AppsViewModel
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.CompanionApp
import rikka.core.ktx.unsafeLazy
import rikka.lifecycle.Status
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.addItemSpacing
import rikka.recyclerview.fixEdgeEffect

class DeveloperActivity : AppBarActivity() {

    private val homeModel: HomeViewModel by viewModels()
    private val appsModel: AppsViewModel by viewModels()
    private val adapter by unsafeLazy { DeveloperAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = HomeActivityBinding.inflate(layoutInflater, rootView, true)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.developer_title)

        homeModel.serviceStatus.observe(this) {
            if (it.status == Status.SUCCESS) updateData()
        }
        appsModel.summary.observe(this) {
            if (it.status == Status.SUCCESS) updateData()
        }

        val recyclerView = binding.list
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()

        val cardSpacing = resources.getDimension(R.dimen.card_spacing)
        val marginHorizontal = resources.getDimension(R.dimen.margin_horizontal)
        val marginVertical = resources.getDimension(R.dimen.margin_vertical)

        val itemSpacing = cardSpacing / 2f
        val edgeSpacingH = marginHorizontal
        val edgeSpacingV = marginVertical - itemSpacing

        recyclerView.addItemSpacing(top = itemSpacing, bottom = itemSpacing)
        recyclerView.addEdgeSpacing(top = edgeSpacingV, bottom = edgeSpacingV, left = edgeSpacingH, right = edgeSpacingH)
    }

    override fun onResume() {
        super.onResume()
        homeModel.reload()
        appsModel.load()
        updateData()
    }

    private fun updateData() {
        adapter.updateData(
            homeModel.serviceStatus.value?.data ?: ServiceStatus(),
            appsModel.summary.value?.data ?: AppDiscoverySummary(0, 0, 0, CompanionApp.isInstalled(this)),
            CompanionApp.status(this, loadIdentity = true)
        )
    }

    /** Called by the companion card after an in-app install so the card reflects the new state. */
    fun refreshCompanion() {
        updateData()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
