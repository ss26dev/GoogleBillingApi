package com.softstackdev.googlebilling

interface ILocalSkuDetails {

    /**
     * @return the timestamp when the sku was procured via Free/24h option
     */
    fun getFree24Timestamp(skuName: String): Long

    /**
     * Set skuName as free for 24 hours
     *
     * @return the current timestamp at which the sku was set as free
     */
    fun setFreeFor24h(skuName: String): Long

    fun isPurchased(skuName: String): Boolean
    fun saveSkuPurchase(skuName: String, purchasedPlayStore: Boolean)

    fun getPurchaseToken(prefixName: String): String
    fun savePurchaseToken(prefixName: String, purchaseToken: String)
    fun getCredits(prefixName: String, purchaseToken: String): Int
    fun saveCredits(prefixName: String, purchaseToken: String, skuCredit: Int)
}