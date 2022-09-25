package com.softstackdev.googlebilling

import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.softstackdev.googlebilling.BillingDependency.localProductDetails
import com.softstackdev.googlebilling.typesProductDetails.AugmentedProductDetails
import com.softstackdev.googlebilling.typesProductDetails.CreditConsumableAugmentedProductDetails
import com.softstackdev.googlebilling.typesProductDetails.IFreeOneDayProductDetails

/**
 * Created by Nena_Schmidt on 07.03.2019.
 */
object AugmentedProductDetailsDao {

    val productDetails = MutableLiveData<MutableList<AugmentedProductDetails>>()
    internal val augmentedProductDetailsList = arrayListOf<AugmentedProductDetails>()

    val consumableAugmentedProductDetailsList = arrayListOf<CreditConsumableAugmentedProductDetails>()


    fun addProductDetail(augmentedProductDetails: AugmentedProductDetails) {
        augmentedProductDetailsList.add(augmentedProductDetails)
    }

    fun updateDetails(productDetailsListPlayStore: MutableList<ProductDetails>) {
        productDetailsListPlayStore.forEach { productDetails ->
            augmentedProductDetailsList.find { it.productId == productDetails.productId }?.apply {

                // title= "MGRS (Map Coordinates)" -> title= "MGRS"
                title = productDetails.title.replace(Regex(""" \(.*\)"""), "")
                description = productDetails.description
                price =  if (!productDetails.subscriptionOfferDetails.isNullOrEmpty()) {
                    val pricingPhase = productDetails.subscriptionOfferDetails!![0].pricingPhases
                            .pricingPhaseList[0]

                    // subscriptionPeriod= P3M -> price= ###/3M
                    "${pricingPhase.formattedPrice}/${pricingPhase.billingPeriod.substring(1)}"
                } else {
                    productDetails.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
                }
                originalProductDetails = productDetails

            }
        }

        notifyUpdateInternalValue()
    }


    fun resetPurchasesForAll() {
        augmentedProductDetailsList.forEach { augmentedProductDetails ->
            augmentedProductDetails.playStorePurchased(false)
        }

        notifyUpdateInternalValue()
    }

    fun updateAcknowledgedPurchases(listWithPurchases: MutableList<Purchase>) {
        listWithPurchases.forEach { purchase ->
            augmentedProductDetailsList.find { it.productId == purchase.products[0] }?.apply {
                playStorePurchased(true)
            }
        }

        notifyUpdateInternalValue()
    }

    fun updateAcknowledgedPurchase(purchase: Purchase) {
        augmentedProductDetailsList.find { it.productId == purchase.products[0] }?.apply {
            playStorePurchased(true)
            notifyUpdateInternalValue()
        }
    }

    fun updateCreditOnConsumed(purchaseToken: String) {
        consumableAugmentedProductDetailsList.find { it.pendingToBeConsumedPurchaseToken == purchaseToken }?.apply {
            addCreditOnePurchase(purchaseToken)
        }
    }

    fun updateMakeFree24(productId: String) {
        augmentedProductDetailsList.find { it.productId == productId }?.apply {
            if (this is IFreeOneDayProductDetails) {
                free24Timestamp = localProductDetails.setFreeFor24h(productId)
                notifyUpdateInternalValue()
            }
        }
    }

    /**
     * this is to notify that values have been changed
     */
    fun notifyUpdateInternalValue() {
        productDetails.postValue(augmentedProductDetailsList)
    }
}