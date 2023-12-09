package com.softstackdev.googlebilling.typesProductDetails.subscription

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.BillingDependency
import com.softstackdev.googlebilling.Products
import com.softstackdev.googlebilling.typesProductDetails.AugmentedProductDetails

/**
 * Created by Nena_Schmidt on 27.10.2020
 */
open class SubscriptionAugmentedProductDetails(productId: String, title: String, description: String) : AugmentedProductDetails(productId, title, description) {

    // key is one of P1W, P4W, P1M, P2M, P3M,P4M, P6M, P8M, P1Y
    var offers = mapOf<String, String>()
    var selectedBillingPeriod = ""

    init {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        Products.SUBSCRIPTION_PRODUCTS.add(product)
        isPurchased = BillingDependency.localProductDetails.isPurchased(productId)

        if(isPurchased) {
            selectedBillingPeriod =
                BillingDependency.localProductDetails.getSelectedBillingPeriod(productId)
        }
    }

    override fun playStorePurchased(
        purchasedPlayStore: Boolean,
        purchaseToken: String,
        orderId: String?
    ) {
        if (purchasedPlayStore != isPurchased) {
            // on local is different from play store
            BillingDependency.localProductDetails.saveSubscription(productId, purchasedPlayStore, selectedBillingPeriod)
            isPurchased = purchasedPlayStore
            this.purchaseToken = purchaseToken
            this.orderId = orderId ?: ""
        }
    }
}