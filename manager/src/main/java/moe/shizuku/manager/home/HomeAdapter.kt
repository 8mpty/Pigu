package moe.shizuku.manager.home

import moe.shizuku.manager.management.AppsViewModel
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.recyclerview.IdBasedRecyclerViewAdapter
import rikka.recyclerview.IndexCreatorPool

class HomeAdapter(private val homeModel: HomeViewModel, private val appsModel: AppsViewModel) :
    IdBasedRecyclerViewAdapter(ArrayList()) {

    init {
        updateData()
        setHasStableIds(true)
    }

    companion object {

        private const val ID_STATUS = 0L
        private const val ID_PAUSED = 1L
        private const val ID_ROOT_WARNING = 2L
        private const val ID_PAIRING_METHODS = 3L
        private const val ID_DETAILS = 4L
        private const val ID_ADB_PERMISSION_LIMITED = 7L
    }

    override fun onCreateCreatorPool(): IndexCreatorPool {
        return IndexCreatorPool()
    }

    fun updateData() {
        val status = homeModel.serviceStatus.value?.data ?: return
        val running = status.isRunning
        val paused = ShizukuSettings.getAccessPaused()

        clear()
        addItem(ServerStatusViewHolder.CREATOR, status, ID_STATUS)

        if (paused) {
            addItem(PauseViewHolder.CREATOR, null, ID_PAUSED)
        }

        if (running && status.uid == 0) {
            addItem(RootWarningViewHolder.CREATOR, null, ID_ROOT_WARNING)
        }

        if (running && !status.permission) {
            addItem(AdbPermissionLimitedViewHolder.CREATOR, status, ID_ADB_PERMISSION_LIMITED)
        }

        // Starting methods would only be refused while paused, so do not offer them.
        if (!paused && UserHandleCompat.myUserId() == 0) {
            addItem(PairingMethodsViewHolder.CREATOR, null, ID_PAIRING_METHODS)
        }

        addItem(
            DetailsViewHolder.CREATOR,
            DetailsData(
                status,
                appsModel.grantedCount.value?.data ?: 0,
                appsModel.packages.value?.data?.size ?: 0
            ),
            ID_DETAILS
        )

        notifyDataSetChanged()
    }
}
