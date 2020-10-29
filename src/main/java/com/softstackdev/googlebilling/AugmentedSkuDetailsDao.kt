package com.softstackdev.googlebilling

import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.softstackdev.googlebilling.BillingDependency.localSkuDetails
import com.softstackdev.googlebilling.typesSkuDetails.AugmentedSkuDetails
import com.softstackdev.googlebilling.typesSkuDetails.ConsumableAugmentedSkuDetails
import com.softstackdev.googlebilling.typesSkuDetails.IFreeOneDaySkuDetails

/**
 * Created by Nena_Schmidt on 07.03.2019.
 */
object AugmentedSkuDetailsDao {

    private var skuDetailsList = MutableLiveData<MutableList<AugmentedSkuDetails>>()
    fun getSkuDetailsList() = skuDetailsList

    val consumableAugmentedSkuDetailsList = arrayListOf<ConsumableAugmentedSkuDetails>()


    fun addSkuDetail(augmentedSkuDetails: AugmentedSkuDetails) {
        val augmentedSkuDetailsList = skuDetailsList.value ?: arrayListOf()
        augmentedSkuDetailsList.add(augmentedSkuDetails)
        skuDetailsList.value = augmentedSkuDetailsList
    }

    fun updateDetails(skuDetailsListPlayStore: MutableList<SkuDetails>) {
        val augmentedSkuDetailsList = skuDetailsList.value ?: return

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

        // this is to notify that values have been changed
        skuDetailsList.postValue(augmentedSkuDetailsList)
    }


    fun updatePurchasesForAll(listWithPurchases: MutableList<Purchase>) {
        val augmentedSkuDetailsList = skuDetailsList.value ?: return

        augmentedSkuDetailsList.forEach { augmentedSkuDetails ->
            listWithPurchases.find { it.sku == augmentedSkuDetails.skuName }
                    ?.let {
                        augmentedSkuDetails.playStorePurchased(true)
                    }
                    ?: run {
                        augmentedSkuDetails.playStorePurchased(false)
                    }
        }

        // this is to notify that values have been changed
        skuDetailsList.postValue(augmentedSkuDetailsList)
    }

    fun updateNewPurchase(purchase: Purchase) {
        val augmentedSkuDetailsList = skuDetailsList.value ?: return

        augmentedSkuDetailsList.find { it.skuName == purchase.sku }?.apply {
            if (this is ConsumableAugmentedSkuDetails) {
                pendingToBeConsumedPurchaseToken = purchase.purchaseToken
                //need to be changed
                BillingRepository.instance?.consumePurchase(purchase.purchaseToken)
            } else {
                playStorePurchased(true)
                // this is to notify that values have been changed
                skuDetailsList.postValue(augmentedSkuDetailsList)
            }
        }
    }

    fun updateCreditOnConsumed(purchaseToken: String){
        consumableAugmentedSkuDetailsList.find { it.pendingToBeConsumedPurchaseToken == purchaseToken }?.apply {
            addCreditOnePurchase(purchaseToken)
        }
    }

    fun updateMakeFree24(skuName: String) {
        val augmentedSkuDetailsList = skuDetailsList.value ?: return

        augmentedSkuDetailsList.find { it.skuName == skuName }?.apply {
            if (this is IFreeOneDaySkuDetails) {
                free24Timestamp = localSkuDetails.setFreeFor24h(skuName)
            }

        } ?: return

        // this is to notify that values have been changed
        skuDetailsList.postValue(augmentedSkuDetailsList)
    }

    fun notifyUpdateInternalValue() {
        val augmentedSkuDetailsList = skuDetailsList.value ?: return

        // this is to notify that values have been changed
        skuDetailsList.postValue(augmentedSkuDetailsList)
    }
}