package com.softstackdev.googlebilling.typesProductDetails

import com.softstackdev.googlebilling.BillingDependency
import com.softstackdev.googlebilling.typesProductDetails.IFreeOneDayProductDetails.Companion.isFree24Available

/**
 * Created by Nena_Schmidt on 27.10.2020
 */
class InappFreeOneDayAugmentedProductDetails : InappAugmentedProductDetails, IFreeOneDayProductDetails {

    constructor(productId: String, title: String, description: String) : super(productId, title, description)
    constructor(productId: String) : super(productId)

    override var free24Timestamp = BillingDependency.localProductDetails.getFree24Timestamp(productId)

    init {
        isFree24Available = true
    }
}