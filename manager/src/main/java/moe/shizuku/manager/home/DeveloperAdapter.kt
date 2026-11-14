package moe.shizuku.manager.home

import moe.shizuku.manager.model.ServiceStatus
import rikka.recyclerview.IdBasedRecyclerViewAdapter
import rikka.recyclerview.IndexCreatorPool

class DeveloperAdapter : IdBasedRecyclerViewAdapter(ArrayList()) {

    companion object {
        private const val ID_APPS = 1L
        private const val ID_TERMINAL = 2L
        private const val ID_AUTOMATION = 3L
        private const val ID_DEVELOPER_GUIDE = 4L
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateCreatorPool(): IndexCreatorPool {
        return IndexCreatorPool()
    }

    fun updateData(status: ServiceStatus, grantedCount: Int) {
        clear()
        addItem(ManageAppsViewHolder.CREATOR, status to grantedCount, ID_APPS)
        addItem(TerminalViewHolder.CREATOR, status, ID_TERMINAL)
        addItem(AutomationViewHolder.CREATOR, null, ID_AUTOMATION)
        addItem(DeveloperGuideViewHolder.CREATOR, null, ID_DEVELOPER_GUIDE)
        notifyDataSetChanged()
    }
}
