package moe.shizuku.manager.home

import moe.shizuku.manager.R
import moe.shizuku.manager.management.AppDiscoverySummary
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.CompanionApp
import rikka.recyclerview.IdBasedRecyclerViewAdapter
import rikka.recyclerview.IndexCreatorPool

class DeveloperAdapter : IdBasedRecyclerViewAdapter(ArrayList()) {

    companion object {
        private const val ID_SECTION_APPS = 10L
        private const val ID_APPS = 1L
        private const val ID_COMPANION = 4L
        private const val ID_AUDIT_LOG = 7L
        private const val ID_SECTION_TOOLS = 11L
        private const val ID_TERMINAL = 2L
        private const val ID_AUTOMATION = 3L
        private const val ID_ACCESSIBILITY = 13L
        private const val ID_SECTION_HELP = 12L
        private const val ID_SERVICE_DOCTOR = 8L
        private const val ID_DIAGNOSTICS = 6L
        private const val ID_DEVELOPER_GUIDE = 5L
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
        auditCount: Int,
        onAuditChanged: () -> Unit,
    ) {
        clear()

        // Apps and access: who can use Pigu, what they did, and the package workaround.
        addItem(DeveloperSectionViewHolder.CREATOR, R.string.developer_section_apps, ID_SECTION_APPS)
        addItem(ManageAppsViewHolder.CREATOR, ManageAppsData(status, summary), ID_APPS)
        addItem(CompanionViewHolder.CREATOR, companion, ID_COMPANION)
        addItem(AuditLogViewHolder.CREATOR, AuditLogData(auditCount, onAuditChanged), ID_AUDIT_LOG)

        // Tools: developer-facing entry points that are not part of normal setup.
        addItem(DeveloperSectionViewHolder.CREATOR, R.string.developer_section_tools, ID_SECTION_TOOLS)
        addItem(TerminalViewHolder.CREATOR, status, ID_TERMINAL)
        addItem(AutomationViewHolder.CREATOR, null, ID_AUTOMATION)
        addItem(AccessibilityCardViewHolder.CREATOR, null, ID_ACCESSIBILITY)

        // Diagnostics and help: fix a problem or report it.
        addItem(DeveloperSectionViewHolder.CREATOR, R.string.developer_section_help, ID_SECTION_HELP)
        addItem(ServiceDoctorViewHolder.CREATOR, null, ID_SERVICE_DOCTOR)
        addItem(
            DiagnosticsViewHolder.CREATOR,
            DiagnosticsData(status, summary.authorized, companion),
            ID_DIAGNOSTICS,
        )
        addItem(DeveloperGuideViewHolder.CREATOR, null, ID_DEVELOPER_GUIDE)
        notifyDataSetChanged()
    }
}
