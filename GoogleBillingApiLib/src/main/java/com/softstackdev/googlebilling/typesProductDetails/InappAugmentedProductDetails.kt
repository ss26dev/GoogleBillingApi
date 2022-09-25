package com.softstackdev.googlebilling.typesProductDetails

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.BillingDependency
import com.softstackdev.googlebilling.Products

/**
 * Created by Nena_Schmidt on 27.10.2020
 */
open class InappAugmentedProductDetails : AugmentedProductDetails {

    constructor(productId: String, title: String, description: String) : super(productId, title, description)
    constructor(productId: String) : super(productId)

    init {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        Products.INAPP_PRODUCTS.add(product)
        isPurchased = BillingDependency.localProductDetails.isPurchased(productId)
    }
}