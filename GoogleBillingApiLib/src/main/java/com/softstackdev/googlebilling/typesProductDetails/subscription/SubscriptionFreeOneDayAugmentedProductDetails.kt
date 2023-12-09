package com.softstackdev.googlebilling.typesProductDetails.subscription

import com.softstackdev.googlebilling.BillingDependency
import com.softstackdev.googlebilling.typesProductDetails.IFreeOneDayProductDetails

class SubscriptionFreeOneDayAugmentedProductDetails(
    productId: String,
    title: String,
    description: String
) : SubscriptionAugmentedProductDetails(productId, title, description), IFreeOneDayProductDetails {
    override var free24Timestamp =
        BillingDependency.localProductDetails.getFree24Timestamp(productId)

    init {
        IFreeOneDayProductDetails.isFree24Available = true
    }
}