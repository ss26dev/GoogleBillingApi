package com.softstackdev.googlebilling.typesProductDetails

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.Products

class InstantConsumableProductDetails(productId: String): AugmentedProductDetails(productId) {

    init {
        product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        Products.CONSUMABLE_PRODUCTS.add(this)
    }
}