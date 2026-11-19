package moe.shizuku.manager.home

import moe.shizuku.manager.management.AppsViewModel
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.recyclerview.IdBasedRecyclerViewAdapter
import rikka.recyclerview.IndexCreatorPool

class HomeAdapter(private val homeModel: HomeViewModel, private val appsModel: AppsViewModel) :
    IdBasedRecyclerViewAdapter(ArrayList()) {

    private var detailsData: DetailsData? = null

    init {
        updateData()
        setHasStableIds(true)
    }

    companion object {

        private const val ID_ACTIONS = 0L
        private const val ID_PAUSED = 1L
        private const val ID_ROOT_WARNING = 2L
        private const val ID_DETAILS = 4L
        private const val ID_ADB_PERMISSION_LIMITED = 7L
    }

    override fun onCreateCreatorPool(): IndexCreatorPool {
        return IndexCreatorPool()
    }

    fun updateData() {
        val status = homeModel.serviceStatus.value?.data ?: ServiceStatus()
        val running = ShizukuStateMachine.isRunning()
        val statusKnown = status.uid >= 0
        val paused = ShizukuSettings.getAccessPaused()

        clear()

        // The service card shows the state and the actions that apply; it is present even when
        // stopped/error so the home screen still explains what is happening.
        if (!paused) {
            addItem(ServiceActionsViewHolder.CREATOR, status, ID_ACTIONS)
        }

        if (paused) {
            addItem(PauseViewHolder.CREATOR, null, ID_PAUSED)
        }

        if (running && statusKnown && status.uid == 0) {
            addItem(RootWarningViewHolder.CREATOR, null, ID_ROOT_WARNING)
        }

        if (running && statusKnown && !status.permission) {
            addItem(AdbPermissionLimitedViewHolder.CREATOR, status, ID_ADB_PERMISSION_LIMITED)
        }

        addItem(
            DetailsViewHolder.CREATOR,
            DetailsData(
                status,
                appsModel.grantedCount.value?.data ?: 0,
                appsModel.packages.value?.data?.size ?: 0,
                homeModel.detailsExpanded,
                ::toggleDetails,
            ).also { detailsData = it },
            ID_DETAILS
        )

        notifyDataSetChanged()
    }

    private fun toggleDetails() {
        homeModel.detailsExpanded = !homeModel.detailsExpanded
        val current = detailsData ?: return
        val updated = current.copy(expanded = homeModel.detailsExpanded)
        detailsData = updated
        // The holder has already updated itself. Only keep the adapter's data in sync so a
        // later refresh/recycle does not restore the old expanded state.
        setFirstItemById(ID_DETAILS, updated)
    }
}
