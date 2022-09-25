package com.softstackdev.googlebilling.typesSkuDetails

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.BillingDependency
import com.softstackdev.googlebilling.SkuProductId

/**
 * Created by Nena_Schmidt on 27.10.2020
 */
open class InappAugmentedSkuDetails : AugmentedSkuDetails {

    constructor(skuName: String, title: String, description: String) : super(skuName, title, description)
    constructor(skuName: String) : super(skuName)

    init {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(skuName)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        SkuProductId.INAPP_SKUS.add(product)
        isPurchased = BillingDependency.localSkuDetails.isPurchased(skuName)
    }
}