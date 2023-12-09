package com.softstackdev.googlebilling.typesProductDetails

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.softstackdev.googlebilling.AugmentedProductDetailsDao
import com.softstackdev.googlebilling.BillingDependency.localProductDetails

/**
 * Created by Nena_Schmidt on 05.03.2019.
 */
abstract class AugmentedProductDetails {

    constructor(productName: String, title: String, description: String) : this(productName) {
        this.title = title
        this.description = description
    }

    constructor(productName: String) {
        this.productId = productName

        AugmentedProductDetailsDao.addProductDetail(this)
    }

    var productId = ""
    lateinit var product: QueryProductDetailsParams.Product

    var price = ""
    var originalProductDetails: ProductDetails? = null

    var isPurchased = false
    var purchaseToken = ""
    var orderId: String = ""


    var title: String = ""
        set(value) {
            // we do this provisionally because
            // AugmentedProductDetailsDao::update overwrite local info with server receivers
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



    open fun playStorePurchased(
        purchasedPlayStore: Boolean,
        purchaseToken: String = "",
        orderId: String? = ""
    ) {
        if (purchasedPlayStore != isPurchased) {
            // on local is different from play store
            localProductDetails.saveProductPurchase(productId, purchasedPlayStore)
            isPurchased = purchasedPlayStore
            this.purchaseToken = purchaseToken
            this.orderId = orderId ?: ""
        }
    }

}