package com.softstackdev.googlebilling

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails

/** Returns null when checkout cannot use the product/period shown in the Shop. */
internal fun buildPurchaseFlowParams(
    product: ProductDetails,
    billingPeriod: String,
    oldPurchaseToken: String = ""
): BillingFlowParams? {
    val offerToken = when (product.productType) {
        BillingClient.ProductType.SUBS -> {
            if (billingPeriod.isBlank()) return null
            // The Shop's price map uses the first pricing phase and last offer for
            // each period. Select the same offer rather than guessing from a plan ID.
            product.subscriptionOfferDetails?.lastOrNull {
                it.pricingPhases.pricingPhaseList.firstOrNull()?.billingPeriod
                    ?.equals(billingPeriod, ignoreCase = true) == true
            }?.offerToken?.takeIf { it.isNotBlank() } ?: return null
        }
        BillingClient.ProductType.INAPP -> {
            val offer = product.oneTimePurchaseOfferDetails ?: return null
            offer.offerToken?.takeIf { it.isNotBlank() }
        }
        else -> return null
    }
    val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
        .setProductDetails(product)
    // Legacy one-time products have no offer token. Calling setOfferToken("")
    // throws in Billing Client 9.1.0; omit it for those products.
    if (offerToken != null) productParams.setOfferToken(offerToken)
    val params = BillingFlowParams.newBuilder()
        .setProductDetailsParamsList(listOf(productParams.build()))
    if (product.productType == BillingClient.ProductType.SUBS && oldPurchaseToken.isNotBlank()) {
        params.setSubscriptionUpdateParams(
            BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(oldPurchaseToken)
                .setSubscriptionReplacementMode(
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE
                ).build()
        )
    }
    return params.build()
}
