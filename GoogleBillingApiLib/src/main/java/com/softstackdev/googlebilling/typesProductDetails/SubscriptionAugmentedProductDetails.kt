package com.softstackdev.googlebilling.typesProductDetails

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.BillingDependency
import com.softstackdev.googlebilling.Products

/**
 * Created by Nena_Schmidt on 27.10.2020
 */
class SubscriptionAugmentedProductDetails(productId: String, title: String, description: String) : AugmentedProductDetails(productId, title, description) {

    init {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        Products.SUBSCRIPTION_PRODUCTS.add(product)
        isPurchased = BillingDependency.localProductDetails.isPurchased(productId)
    }
}