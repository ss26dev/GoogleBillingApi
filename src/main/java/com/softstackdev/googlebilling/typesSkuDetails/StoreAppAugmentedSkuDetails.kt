package com.softstackdev.googlebilling.typesSkuDetails

import com.softstackdev.googlebilling.SkuProductId

/**
 * Created by Nena_Schmidt on 28.10.2020
 */
class StoreAppAugmentedSkuDetails(skuName: String, title: String, description: String,
                                  val packageName: String) : AugmentedSkuDetails(skuName, title, description) {

    init {
        SkuProductId.STORE_APP_SKUS.add(skuName)
    }
}