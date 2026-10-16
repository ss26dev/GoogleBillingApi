package com.softstackdev.googlebilling

/** Token-free checkout diagnostics. COMPLETED means verified entitlement delivery, not net revenue. */
data class CheckoutEvent(
    val requestId: Long,
    val productId: String,
    val billingPeriod: String,
    val status: CheckoutStatus,
    val stage: CheckoutStage,
    val responseCode: Int? = null,
    val reason: CheckoutReason? = null
)

enum class CheckoutStatus {
    LAUNCHED, PENDING, VERIFIED, COMPLETED, CANCELLED, ALREADY_OWNED,
    FAILED, PROCESSING_FAILED, UNRESOLVED
}

enum class CheckoutStage { LAUNCH, PURCHASE, VERIFICATION, ACKNOWLEDGEMENT, CONSUMPTION }

enum class CheckoutReason {
    PRODUCT_UNAVAILABLE, ACTIVITY_UNAVAILABLE, OFFER_UNAVAILABLE, CHECKOUT_IN_PROGRESS,
    BILLING_RESPONSE, LAUNCH_EXCEPTION, EMPTY_PURCHASES, INVALID_SIGNATURE,
    UNKNOWN_PURCHASE_STATE, EXISTING_PURCHASE, TRACKING_EXPIRED, EXTERNAL_STORE
}
