package com.peterlaurence.trekme.viewmodel.mapview

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peterlaurence.trekme.billing.ign.*
import com.peterlaurence.trekme.core.map.Map
import com.peterlaurence.trekme.core.settings.RotationMode
import com.peterlaurence.trekme.core.settings.Settings
import com.peterlaurence.trekme.model.map.MapModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus

/**
 * The view model of the fragment which displays [Map]s.
 *
 * @author peterLaurence on 24/08/2019
 */
class MapViewViewModel @ViewModelInject constructor(
        private val persistenceStrategy: PersistenceStrategy,
        private val settings: Settings,
        private val billing: Billing
) : ViewModel() {
    private val eventBus = EventBus.getDefault()

    /**
     * @return a [Map] instance, or null if there is none or there's a license issue
     */
    fun getMap(): Map? {
        val map = MapModel.getCurrentMap()
        if (map != null) {
            viewModelScope.launch {
                checkForIgnLicense(map)
            }
        }
        return map
    }

    fun getMagnifyingFactor(): Int = settings.getMagnifyingFactor()

    fun getRotationMode(): RotationMode = settings.getRotationMode()

    fun getDefineScaleCentered(): Boolean = settings.getDefineScaleCentered()

    fun getScaleCentered(): Float = settings.getScaleCentered()

    private suspend fun checkForIgnLicense(map: Map): Boolean {
        if (map.origin != Map.MapOrigin.IGN_LICENSED) return true

        /**
         * If the persistence file doesn't exists and the license is proven to be purchased
         * and still valid, create the persistence file.
         * Otherwise, warn the user that the license is either missing or expired.
         */
        suspend fun onFailureToReadFile(): Boolean {
            // missing license or something else wrong
            return billing.getIgnLicensePurchase()?.let {
                persistenceStrategy.persist(LicenseInfo(it.purchaseTime))
                true
            } ?: {
                eventBus.post(ErrorIgnLicenseEvent(map))
                false
            }()
        }

        return withContext(Dispatchers.IO) {
            persistenceStrategy.getLicenseInfo()?.let {
                when (val accessState = checkTime(it.purchaseTimeMillis)) {
                    is AccessGranted -> true
                    is GracePeriod -> {
                        eventBus.post(GracePeriodIgnEvent(map, accessState.remainingDays))
                        true
                    }
                    is AccessDeniedLicenseOutdated -> {
                        eventBus.post(OutdatedIgnLicenseEvent(map))
                        false
                    }
                }
            } ?: onFailureToReadFile()
        }
    }
}

data class OutdatedIgnLicenseEvent(val map: Map)
data class ErrorIgnLicenseEvent(val map: Map)
data class GracePeriodIgnEvent(val map: Map, val remainingDays: Int)