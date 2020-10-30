package com.softstackdev.googlebilling

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.softstackdev.googlebilling.typesSkuDetails.AugmentedSkuDetails
import com.softstackdev.googlebilling.typesSkuDetails.IFreeOneDaySkuDetails

/**
 * Created by Nena_Schmidt on 05.03.2019.
 */
class BillingViewModel(application: Application) : AndroidViewModel(application) {

    val skuDetailsListLiveData: MutableLiveData<MutableList<AugmentedSkuDetails>>
    val playStoreLoadedLiveData: MutableLiveData<Boolean>

    private val repository: BillingRepository

    init {
        repository = BillingRepository.getInstance(application)
//        repository.startDataSourceConnections()

        skuDetailsListLiveData = AugmentedSkuDetailsDao.skuDetailsList
        playStoreLoadedLiveData = BillingRepository.playStoreLoaded
    }

    fun makePurchase(activity: Activity, augmentedSkuDetails: AugmentedSkuDetails) {
        repository.makePurchase(activity, augmentedSkuDetails)
    }

    fun makeFree24(skuName: String) {
        AugmentedSkuDetailsDao.updateMakeFree24(skuName)
    }

    fun isFree24Available() = IFreeOneDaySkuDetails.isFree24Available
}
