package com.softstackdev.googlebilling.typesProductDetails

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.AugmentedProductDetailsDao
import com.softstackdev.googlebilling.AugmentedProductDetailsDao.consumableAugmentedProductDetailsList
import com.softstackdev.googlebilling.BillingDependency.localProductDetails
import com.softstackdev.googlebilling.Products

/**
 * Created by Nena_Schmidt on 28.10.2020
 */
class CreditConsumableAugmentedProductDetails(productId: String, title: String, description: String,
                                              private val prefixName: String, private val creditOfOnePurchase: Int,
                                              val creditMaximumAllowed: Int) : AugmentedProductDetails(productId, title, description) {

    var pendingToBeConsumedPurchaseToken = ""
    var lastConsumedPurchaseToken = localProductDetails.getPurchaseToken(prefixName)

    @Volatile
    var creditNos = localProductDetails.getCredits(prefixName, lastConsumedPurchaseToken)
        private set

    init {
        this.product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        Products.CONSUMABLE_PRODUCTS.add(this)
        consumableAugmentedProductDetailsList.add(this)
    }

    fun availableCredit() = creditNos > 0

    fun addCreditOnePurchase(purchaseToken: String) {
        // the purchaseToken has been changed
        localProductDetails.savePurchaseToken(prefixName, purchaseToken)
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
        localProductDetails.saveCredits(prefixName, lastConsumedPurchaseToken, creditNos)

        AugmentedProductDetailsDao.notifyUpdateInternalValue()
    }
}