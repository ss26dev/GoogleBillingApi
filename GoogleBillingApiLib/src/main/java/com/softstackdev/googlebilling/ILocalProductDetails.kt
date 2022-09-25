package com.softstackdev.googlebilling

interface ILocalProductDetails {

    /**
     * @return the timestamp when the product was procured via Free/24h option
     */
    fun getFree24Timestamp(productName: String): Long

    /**
     * Set productName as free for 24 hours
     *
     * @return the current timestamp at which the product was set as free
     */
    fun setFreeFor24h(productName: String): Long

    fun isPurchased(productName: String): Boolean
    fun saveProductPurchase(productName: String, purchasedPlayStore: Boolean)

    fun getPurchaseToken(prefixName: String): String
    fun savePurchaseToken(prefixName: String, purchaseToken: String)
    fun getCredits(prefixName: String, purchaseToken: String): Int
    fun saveCredits(prefixName: String, purchaseToken: String, productCredit: Int)
}