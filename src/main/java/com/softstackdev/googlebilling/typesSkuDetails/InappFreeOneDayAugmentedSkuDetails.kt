package com.softstackdev.googlebilling.typesSkuDetails

import com.softstackdev.googlebilling.BillingDependency
import com.softstackdev.googlebilling.typesSkuDetails.IFreeOneDaySkuDetails.Companion.isFree24Available

/**
 * Created by Nena_Schmidt on 27.10.2020
 */
class InappFreeOneDayAugmentedSkuDetails : InappAugmentedSkuDetails, IFreeOneDaySkuDetails {

    constructor(skuName: String, title: String, description: String) : super(skuName, title, description)
    constructor(skuName: String) : super(skuName)

    override var free24Timestamp = BillingDependency.localSkuDetails.getFree24Timestamp(skuName)

    init {
        isFree24Available = true
    }
}