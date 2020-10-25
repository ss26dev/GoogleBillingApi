package com.softstackdev.googlebilling

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

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

        skuDetailsListLiveData = AugmentedSkuDetailsDao.getSkuDetailsList()
        playStoreLoadedLiveData = BillingRepository.playStoreLoaded
    }

    fun makePurchase(activity: Activity, augmentedSkuDetails: AugmentedSkuDetails) {
        repository.makePurchase(activity, augmentedSkuDetails)
    }

    fun makeFree24(skuName: String) {
        AugmentedSkuDetailsDao.updateMakeFree24(skuName)
    }

    fun isFree24Available() = AugmentedSkuDetails.isFree24Available
}
