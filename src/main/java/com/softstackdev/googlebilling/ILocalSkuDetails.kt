package com.softstackdev.googlebilling

interface ILocalSkuDetails {

    fun getFree24Timestamp(skuName: String): Long
    fun setFreeFor24h(skuName: String): Long

    fun isPurchased(skuName: String): Boolean
    fun saveSkuPurchase(skuName: String, purchasedPlayStore: Boolean)

    fun getPurchaseToken(prefixName: String): String
    fun savePurchaseToken(prefixName: String, purchaseToken: String)
    fun getCredits(prefixName: String, purchaseToken: String): Int
    fun saveCredits(prefixName: String, purchaseToken: String, skuCredit: Int)
}