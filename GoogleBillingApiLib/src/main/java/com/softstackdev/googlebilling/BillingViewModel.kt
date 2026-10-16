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
     * @param billingPeriod e.g. p1m, p6m, p1y
     * @return true if checkout was accepted; use the callback overload for outcomes.
     */
    fun makePurchase(
        activity: Activity,
        augmentedProductDetails: AugmentedProductDetails,
        billingPeriod: String = ""
    ): Boolean {
        return repository.makePurchase(activity, augmentedProductDetails, billingPeriod)
    }

    /** Callback diagnostics survive Activity recreation in this process; do not capture a View/Activity. */
    fun makePurchase(
        activity: Activity,
        augmentedProductDetails: AugmentedProductDetails,
        billingPeriod: String,
        onCheckoutEvent: (CheckoutEvent) -> Unit
    ): Boolean = repository.makePurchase(activity, augmentedProductDetails, billingPeriod, onCheckoutEvent)

    fun makeFree24(productId: String) {
        AugmentedProductDetailsDao.updateMakeFree24(productId)
    }

    fun isFree24Available() = IFreeOneDayProductDetails.isFree24Available
}
