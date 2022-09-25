package com.softstackdev.googlebilling

import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.typesProductDetails.AugmentedProductDetails

object Products {

    val INAPP_PRODUCTS = mutableListOf<QueryProductDetailsParams.Product>()
    val SUBSCRIPTION_PRODUCTS = mutableListOf<QueryProductDetailsParams.Product>()
    val CONSUMABLE_PRODUCTS = mutableListOf<AugmentedProductDetails>()
    val STORE_APP_PRODUCTS = mutableListOf<QueryProductDetailsParams.Product>()

    fun getQueryProductList(
            augmentedProductsList: List<AugmentedProductDetails>
    ): List<QueryProductDetailsParams.Product> {
        return augmentedProductsList.map { it.product }
    }
}