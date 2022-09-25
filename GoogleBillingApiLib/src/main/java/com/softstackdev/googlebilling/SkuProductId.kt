package com.softstackdev.googlebilling

import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.typesSkuDetails.AugmentedSkuDetails

object SkuProductId {

    val INAPP_SKUS = mutableListOf<QueryProductDetailsParams.Product>()
    val SUBSCRIPTION_SKUS = mutableListOf<QueryProductDetailsParams.Product>()
    val CONSUMABLE_SKUS = mutableListOf<AugmentedSkuDetails>()
    val STORE_APP_SKUS = mutableListOf<QueryProductDetailsParams.Product>()

    fun getQueryProductList(
        augmentedProductsList: List<AugmentedSkuDetails>
    ): List<QueryProductDetailsParams.Product> {
        return augmentedProductsList.map { it.product }
    }
}