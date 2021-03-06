package com.softstackdev.googlebilling.typesSkuDetails

import com.softstackdev.googlebilling.AugmentedSkuDetailsDao
import com.softstackdev.googlebilling.AugmentedSkuDetailsDao.consumableAugmentedSkuDetailsList
import com.softstackdev.googlebilling.BillingDependency.localSkuDetails
import com.softstackdev.googlebilling.SkuProductId

/**
 * Created by Nena_Schmidt on 28.10.2020
 */
class ConsumableAugmentedSkuDetails(skuName: String, title: String, description: String,
                                    private val prefixName: String, private val creditOfOnePurchase: Int,
                                    val creditMaximumAllowed: Int) : AugmentedSkuDetails(skuName, title, description) {

    var pendingToBeConsumedPurchaseToken = ""
    var lastConsumedPurchaseToken = localSkuDetails.getPurchaseToken(prefixName)

    @Volatile
    var creditNos = localSkuDetails.getCredits(prefixName, lastConsumedPurchaseToken)
        private set

    init {
        SkuProductId.CONSUMABLE_SKUS.add(skuName)
        consumableAugmentedSkuDetailsList.add(this)
    }

    fun availableCredit() = creditNos > 0

    fun addCreditOnePurchase(purchaseToken: String) {
        // the purchaseToken has been changed
        localSkuDetails.savePurchaseToken(prefixName, purchaseToken)
        lastConsumedPurchaseToken = purchaseToken

        addCredit(creditOfOnePurchase)
    }

    fun decrementCredit() {
        addCredit(-1)
    }

    private fun addCredit(creditToAdd: Int) {
        synchronized(this) {
            creditNos += creditToAdd
        }
        localSkuDetails.saveCredits(prefixName, lastConsumedPurchaseToken, creditNos)

        AugmentedSkuDetailsDao.notifyUpdateInternalValue()
    }
}