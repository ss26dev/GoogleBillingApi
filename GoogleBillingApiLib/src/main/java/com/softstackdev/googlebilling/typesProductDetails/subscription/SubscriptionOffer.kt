package com.softstackdev.googlebilling.typesProductDetails.subscription

/**
 * @param price the formatted price, including currency
 * @param billingPeriod one of P1M, P3M, P6M, P1Y
 */
data class SubscriptionOffer(val price: String, val billingPeriod: String)
