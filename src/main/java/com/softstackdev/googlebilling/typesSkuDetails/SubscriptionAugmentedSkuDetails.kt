package com.softstackdev.googlebilling.typesSkuDetails

import com.softstackdev.googlebilling.BillingDependency
import com.softstackdev.googlebilling.SkuProductId

/**
 * Created by Nena_Schmidt on 27.10.2020
 */
class SubscriptionAugmentedSkuDetails(skuName: String, title: String, description: String) : AugmentedSkuDetails(skuName, title, description) {

    init {
        SkuProductId.SUBS_SKUS.add(skuName)
        isPurchased = BillingDependency.localSkuDetails.isPurchased(skuName)
    }
}