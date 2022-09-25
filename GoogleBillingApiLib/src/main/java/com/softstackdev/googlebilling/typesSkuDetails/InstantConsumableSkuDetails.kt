package com.softstackdev.googlebilling.typesSkuDetails

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.SkuProductId

class InstantConsumableSkuDetails(skuName: String): AugmentedSkuDetails(skuName) {

    init {
        product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(skuName)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        SkuProductId.CONSUMABLE_SKUS.add(this)
    }
}