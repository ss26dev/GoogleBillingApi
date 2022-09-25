package com.softstackdev.googlebilling.sample.billing

import com.softstackdev.googlebilling.ILocalSkuDetails

object LocalProductDetails: ILocalSkuDetails {

    override fun getFree24Timestamp(skuName: String): Long {
        TODO("Not yet implemented")
    }

    override fun setFreeFor24h(skuName: String): Long {
        TODO("Not yet implemented")
    }

    override fun isPurchased(skuName: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun saveSkuPurchase(skuName: String, purchasedPlayStore: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getPurchaseToken(prefixName: String): String {
        TODO("Not yet implemented")
    }

    override fun savePurchaseToken(prefixName: String, purchaseToken: String) {
        TODO("Not yet implemented")
    }

    override fun getCredits(prefixName: String, purchaseToken: String): Int {
        TODO("Not yet implemented")
    }

    override fun saveCredits(prefixName: String, purchaseToken: String, skuCredit: Int) {
        TODO("Not yet implemented")
    }
}