package moe.shizuku.manager.home

import moe.shizuku.manager.management.AppDiscoverySummary
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.CompanionApp
import rikka.recyclerview.IdBasedRecyclerViewAdapter
import rikka.recyclerview.IndexCreatorPool

class DeveloperAdapter : IdBasedRecyclerViewAdapter(ArrayList()) {

    companion object {
        private const val ID_APPS = 1L
        private const val ID_TERMINAL = 2L
        private const val ID_AUTOMATION = 3L
        private const val ID_COMPANION = 4L
        private const val ID_DEVELOPER_GUIDE = 5L
        private const val ID_DIAGNOSTICS = 6L
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateCreatorPool(): IndexCreatorPool {
        return IndexCreatorPool()
    }

    fun updateData(
        status: ServiceStatus,
        summary: AppDiscoverySummary,
        companion: CompanionApp.Status,
    ) {
        clear()
        addItem(ManageAppsViewHolder.CREATOR, ManageAppsData(status, summary), ID_APPS)
        addItem(TerminalViewHolder.CREATOR, status, ID_TERMINAL)
        addItem(CompanionViewHolder.CREATOR, companion, ID_COMPANION)
        addItem(AutomationViewHolder.CREATOR, null, ID_AUTOMATION)
        addItem(
            DiagnosticsViewHolder.CREATOR,
            DiagnosticsData(status, summary.authorized, companion),
            ID_DIAGNOSTICS,
        )
        addItem(DeveloperGuideViewHolder.CREATOR, null, ID_DEVELOPER_GUIDE)
        notifyDataSetChanged()
    }
}
