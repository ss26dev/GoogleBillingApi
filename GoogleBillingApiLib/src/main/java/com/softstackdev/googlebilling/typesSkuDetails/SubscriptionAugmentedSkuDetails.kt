package com.softstackdev.googlebilling.typesSkuDetails

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.BillingDependency
import com.softstackdev.googlebilling.SkuProductId

/**
 * Created by Nena_Schmidt on 27.10.2020
 */
class SubscriptionAugmentedSkuDetails(skuName: String, title: String, description: String) : AugmentedSkuDetails(skuName, title, description) {

    init {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(skuName)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        SkuProductId.SUBSCRIPTION_SKUS.add(product)
        isPurchased = BillingDependency.localSkuDetails.isPurchased(skuName)
    }
}