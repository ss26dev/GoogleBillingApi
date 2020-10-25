package com.softstackdev.googlebilling

interface ILocalSkuDetails {

    fun getFree24Timestamp(skuName: String): Long
    fun setFreeFor24h(skuName: String): Long

    fun isPurchased(skuName: String): Boolean
    fun saveSkuPurchase(skuName: String, purchasedPlayStore: Boolean)
    fun getPurchaseToken(prefixName: String): String
}