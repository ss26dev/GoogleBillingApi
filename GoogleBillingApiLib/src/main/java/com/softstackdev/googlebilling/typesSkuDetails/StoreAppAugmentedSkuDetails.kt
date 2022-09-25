package com.softstackdev.googlebilling.typesSkuDetails

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.SkuProductId

/**
 * Created by Nena_Schmidt on 28.10.2020
 */
class StoreAppAugmentedSkuDetails(skuName: String, title: String, description: String,
                                  val packageName: String) : AugmentedSkuDetails(skuName, title, description) {

    init {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(skuName)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        SkuProductId.STORE_APP_SKUS.add(product)
    }
}