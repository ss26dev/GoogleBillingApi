package com.softstackdev.googlebilling

import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.softstackdev.googlebilling.BillingDependency.localSkuDetails
import com.softstackdev.googlebilling.typesSkuDetails.AugmentedSkuDetails
import com.softstackdev.googlebilling.typesSkuDetails.CreditConsumableAugmentedSkuDetails
import com.softstackdev.googlebilling.typesSkuDetails.IFreeOneDaySkuDetails

/**
 * Created by Nena_Schmidt on 07.03.2019.
 */
object AugmentedSkuDetailsDao {

    val skuDetailsList = MutableLiveData<MutableList<AugmentedSkuDetails>>()
    internal val augmentedSkuDetailsList = arrayListOf<AugmentedSkuDetails>()

    val consumableAugmentedSkuDetailsList = arrayListOf<CreditConsumableAugmentedSkuDetails>()


    fun addSkuDetail(augmentedSkuDetails: AugmentedSkuDetails) {
        augmentedSkuDetailsList.add(augmentedSkuDetails)
    }

    fun updateDetails(skuDetailsListPlayStore: MutableList<SkuDetails>) {
        skuDetailsListPlayStore.forEach { skuDetails ->
            augmentedSkuDetailsList.find { it.skuName == skuDetails.sku }?.apply {

                // title= "MGRS (Map Coordinates)" -> title= "MGRS"
                title = skuDetails.title.replace(Regex(""" \(.*\)"""), "")
                description = skuDetails.description
                price = skuDetails.price
                if (!skuDetails.subscriptionPeriod.isNullOrEmpty()) {
                    // subscriptionPeriod= P3M -> price= ###/3M
                    price += "/" + skuDetails.subscriptionPeriod.substring(1)
                }
                originalJson = skuDetails.originalJson

            }
        }

        notifyUpdateInternalValue()
    }


    fun resetPurchasesForAll() {
        augmentedSkuDetailsList.forEach { augmentedSkuDetails ->
            augmentedSkuDetails.playStorePurchased(false)
        }

        notifyUpdateInternalValue()
    }

    fun updateAcknowledgedPurchases(listWithPurchases: MutableList<Purchase>) {
        listWithPurchases.forEach { purchase ->
            augmentedSkuDetailsList.find { it.skuName == purchase.skus[0] }?.apply {
                playStorePurchased(true)
            }
        }

        notifyUpdateInternalValue()
    }

    fun updateAcknowledgedPurchase(purchase: Purchase) {
        augmentedSkuDetailsList.find { it.skuName == purchase.skus[0] }?.apply {
            playStorePurchased(true)
            notifyUpdateInternalValue()
        }
    }

    fun updateCreditOnConsumed(purchaseToken: String) {
        consumableAugmentedSkuDetailsList.find { it.pendingToBeConsumedPurchaseToken == purchaseToken }?.apply {
            addCreditOnePurchase(purchaseToken)
        }
    }

    fun updateMakeFree24(skuName: String) {
        augmentedSkuDetailsList.find { it.skuName == skuName }?.apply {
            if (this is IFreeOneDaySkuDetails) {
                free24Timestamp = localSkuDetails.setFreeFor24h(skuName)
                notifyUpdateInternalValue()
            }
        }
    }

    /**
     * this is to notify that values have been changed
     */
    fun notifyUpdateInternalValue() {
        skuDetailsList.postValue(augmentedSkuDetailsList)
    }
}