package com.softstackdev.googlebilling.typesSkuDetails

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.AugmentedSkuDetailsDao
import com.softstackdev.googlebilling.BillingDependency.localSkuDetails

/**
 * Created by Nena_Schmidt on 05.03.2019.
 */
abstract class AugmentedSkuDetails {

    constructor(skuName: String, title: String, description: String) : this(skuName) {
        this.title = title
        this.description = description
    }

    constructor(skuName: String) {
        this.skuName = skuName

        AugmentedSkuDetailsDao.addSkuDetail(this)
    }

    var skuName = ""
    lateinit var product: QueryProductDetailsParams.Product

    var price = ""
    var originalProductDetails: ProductDetails? = null

    var isPurchased = false


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

}