package com.softstackdev.googlebilling.typesSkuDetails

/**
 * Created by Nena_Schmidt on 27.10.2020
 */
interface IFreeOneDaySkuDetails {

    companion object {

        var isFree24Available = false
    }

    var free24Timestamp: Long
}