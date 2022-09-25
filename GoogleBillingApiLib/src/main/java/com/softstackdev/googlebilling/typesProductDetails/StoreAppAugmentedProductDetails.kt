package com.softstackdev.googlebilling.typesProductDetails

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.Products

/**
 * Created by Nena_Schmidt on 28.10.2020
 */
class StoreAppAugmentedProductDetails(productId: String, title: String, description: String,
                                  val packageName: String) : AugmentedProductDetails(productId, title, description) {

    init {
        val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

        Products.STORE_APP_PRODUCTS.add(product)
    }
}