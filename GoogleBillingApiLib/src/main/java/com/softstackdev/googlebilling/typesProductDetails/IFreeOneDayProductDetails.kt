package com.softstackdev.googlebilling.typesProductDetails

/**
 * Created by Nena_Schmidt on 27.10.2020
 */
interface IFreeOneDayProductDetails {

    companion object {

        var isFree24Available = false
    }

    var free24Timestamp: Long
}