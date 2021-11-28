package com.softstackdev.googlebilling.typesSkuDetails

import com.softstackdev.googlebilling.SkuProductId

class InstantConsumableSkuDetails(skuName: String): AugmentedSkuDetails(skuName) {

    init {
        SkuProductId.CONSUMABLE_SKUS.add(skuName)
    }
}