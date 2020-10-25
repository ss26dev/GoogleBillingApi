package com.softstackdev.googlebilling

import com.softstackdev.googlebilling.BillingDependency.localSkuDetails

/**
 * Created by Nena_Schmidt on 05.03.2019.
 */
class AugmentedSkuDetails {

    companion object {
        var isFree24Available = false
    }

    constructor(skuName: String, title: String, description: String) : this(skuName) {
        this.title = title
        this.description = description
    }

    constructor(skuName: String) {
        this.skuName = skuName

        AugmentedSkuDetailsDao.addSkuDetail(this)
    }

    var skuName = ""

    var price = ""
    var originalJson = ""

    var isPurchased = false
    var purchaseToken = ""

    var free24CreditVisible = false
    var free24Timestamp = 0L


    var title: String = ""
        set(value) {
            // we do this provisionally because
            // AugmentedSkuDetailsDao::update overwrite local info with server receivers
            // Pro and AdsSubs we create from context
            if (field.isEmpty()) {
                field = value
            }
        }

    var description: String = ""
        set(value) {
            // same as title
            if (field.isEmpty()) {
                field = value
            }
        }



    fun playStorePurchased(purchasedPlayStore: Boolean) {
        if (purchasedPlayStore != isPurchased) {
            //on local is different from play store
            localSkuDetails.saveSkuPurchase(skuName, purchasedPlayStore)
            isPurchased = purchasedPlayStore
        }
    }


    fun inappSku() = apply {
        SkuProductId.INAPP_SKUS.add(skuName)
        isPurchased = localSkuDetails.isPurchased(skuName)
    }

    fun inappSkuFree24() = apply {
        inappSku()

        free24CreditVisible = true
        isFree24Available = true
        free24Timestamp = localSkuDetails.getFree24Timestamp(skuName)
    }

    fun subsSku() = apply {
        SkuProductId.SUBS_SKUS.add(skuName)
        isPurchased = localSkuDetails.isPurchased(skuName)
    }

}