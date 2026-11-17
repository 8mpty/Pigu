package moe.shizuku.manager.management

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.utils.CompanionApp
import rikka.lifecycle.Resource

data class AppDiscoverySummary(
    val total: Int,
    val authorized: Int,
    val legacyClients: Int,
    val companionInstalled: Boolean,
) {
    val companionCandidates: Int
        get() = if (companionInstalled) 0 else legacyClients
}

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext

    private val _packages = MutableLiveData<Resource<List<PackageInfo>>>()
    val packages = _packages as LiveData<Resource<List<PackageInfo>>>

    private val _grantedCount = MutableLiveData<Resource<Int>>()
    val grantedCount = _grantedCount as LiveData<Resource<Int>>

    private val _summary = MutableLiveData<Resource<AppDiscoverySummary>>()
    val summary = _summary as LiveData<Resource<AppDiscoverySummary>>

    private val _grantedUids = MutableLiveData<Resource<Set<Int>>>()
    val grantedUids = _grantedUids as LiveData<Resource<Set<Int>>>

    fun load(onlyCount: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list: MutableList<PackageInfo> = ArrayList()
                val granted = HashSet<Int>()
                var count = 0
                var legacy = 0
                for (pi in AuthorizationManager.getPackages(
                    exclude = listOf(appContext.packageName)
                )) {
                    list.add(pi)
                    val uid = pi.applicationInfo!!.uid
                    if (AuthorizationManager.granted(pi.packageName, uid)) {
                        count++
                        granted.add(uid)
                    }
                    val modern = pi.applicationInfo?.metaData
                        ?.getBoolean("moe.shizuku.client.V3_SUPPORT") == true
                    if (!modern) legacy++
                }
                if (!onlyCount) _packages.postValue(Resource.success(list))
                _grantedCount.postValue(Resource.success(count))
                _grantedUids.postValue(Resource.success(granted))
                _summary.postValue(
                    Resource.success(
                        AppDiscoverySummary(
                            total = list.size,
                            authorized = count,
                            legacyClients = legacy,
                            companionInstalled = CompanionApp.isInstalled(appContext),
                        )
                    )
                )
            } catch (e: CancellationException) {

            } catch (e: Throwable) {
                _packages.postValue(Resource.error(e, null))
                _grantedCount.postValue(Resource.error(e, 0))
                _grantedUids.postValue(Resource.error(e, emptySet()))
                _summary.postValue(Resource.error(e, null))
            }
        }
    }
    
}
