package com.softstackdev.googlebilling.sample.billing

import com.softstackdev.googlebilling.typesSkuDetails.AugmentedSkuDetails
import com.softstackdev.googlebilling.typesSkuDetails.InstantConsumableSkuDetails

/**
 * Created by Nena_Schmidt on 08.03.2019.
 */
object SkuProductId {

    const val DONATE_1_SKU_NAME = "donate_1"
    val DONATE_1: AugmentedSkuDetails by lazy {
        InstantConsumableSkuDetails(DONATE_1_SKU_NAME)
    }
}
