package com.softstackdev.googlebilling.typesProductDetails

/**
 * Created by Nena_Schmidt on 27.10.2020
 */
interface IFreeOneDayProductDetails {

    companion object {
        /**
         * When we have at least one FreeOneDay product type, this flag will be set to true.
         * It's used to control whether we should initialize the rewarded ads or not
         */
        var isFree24Available = false
    }

    var free24Timestamp: Long
}