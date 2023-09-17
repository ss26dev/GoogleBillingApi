package com.softstackdev.googlebilling

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.softstackdev.googlebilling.typesProductDetails.AugmentedProductDetails
import com.softstackdev.googlebilling.typesProductDetails.IFreeOneDayProductDetails

/**
 * Created by Nena_Schmidt on 05.03.2019.
 */
class BillingViewModel(application: Application) : AndroidViewModel(application) {

    val productDetailsListLiveData: MutableLiveData<MutableList<AugmentedProductDetails>>
    val playStoreLoadedLiveData: MutableLiveData<Boolean>

    private val repository: BillingRepository

    init {
        repository = BillingRepository.getInstance(application)
//        repository.startDataSourceConnections()

        productDetailsListLiveData = AugmentedProductDetailsDao.productDetails
        playStoreLoadedLiveData = BillingRepository.playStoreLoaded
    }

    /**
     * @return true if purchase was executed.
     */
    fun makePurchase(
        activity: Activity,
        augmentedProductDetails: AugmentedProductDetails
    ): Boolean {
        return repository.makePurchase(activity, augmentedProductDetails)
    }

    fun makeFree24(productId: String) {
        AugmentedProductDetailsDao.updateMakeFree24(productId)
    }

    fun isFree24Available() = IFreeOneDayProductDetails.isFree24Available
}
