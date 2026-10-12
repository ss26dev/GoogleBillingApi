package com.softstackdev.googlebilling

import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PurchaseFlowParamsTest {
    @Test
    fun legacyOneTimeProductDoesNotPassAnEmptyToken() {
        val params = buildPurchaseFlowParams(oneTimeProduct(), "p1m", "old-consumable-token")
        assertNotNull(params)
        assertNull(checkoutProduct(params!!).zzb())
        assertNull(subscriptionUpdate(params))
    }

    @Test
    fun tokenizedOneTimeOfferKeepsItsToken() {
        val params = buildPurchaseFlowParams(oneTimeProduct("one-time-token"), "")!!
        assertEquals("one-time-token", checkoutProduct(params).zzb())
    }

    @Test
    fun subscriptionUsesDisplayedPeriodEvenWhenPlanIdHasNoPeriodInItsName() {
        val params = buildPurchaseFlowParams(subscriptionProduct(), "p1y")!!
        assertEquals("year-token", checkoutProduct(params).zzb())
    }

    @Test
    fun subscriptionWithoutSelectedOfferFailsSafely() {
        assertNull(buildPurchaseFlowParams(subscriptionProduct(), "p6m"))
        assertNull(buildPurchaseFlowParams(subscriptionProduct(), ""))
        assertNull(buildPurchaseFlowParams(subscriptionProduct(""), "p1y"))
    }

    @Test
    fun subscriptionReplacementKeepsOldPurchaseToken() {
        val params = buildPurchaseFlowParams(subscriptionProduct(), "p1m", "previous-token")!!
        assertNotNull(subscriptionUpdate(params))
    }

    @Test
    fun unfetchedOneTimeOfferDoesNotStartCheckout() {
        assertNull(buildPurchaseFlowParams(product("""{"productId":"credits","type":"inapp"}"""), ""))
    }

    private fun oneTimeProduct(token: String = "") = product("""
        {"productId":"credits","type":"inapp","oneTimePurchaseOfferDetails":{
          "formattedPrice":"€1.00","priceAmountMicros":1000000,"priceCurrencyCode":"EUR",
          "offerIdToken":"$token"}}
    """)

    private fun subscriptionProduct(yearToken: String = "year-token") = product("""
        {"productId":"pro","type":"subs","subscriptionOfferDetails":[
          {"basePlanId":"monthly-plan","offerIdToken":"month-token","pricingPhases":[
            {"billingPeriod":"P1M","formattedPrice":"€1.00","priceAmountMicros":1000000,
             "priceCurrencyCode":"EUR","recurrenceMode":1,"billingCycleCount":0}]},
          {"basePlanId":"annual-plan","offerIdToken":"$yearToken","pricingPhases":[
            {"billingPeriod":"P1Y","formattedPrice":"€10.00","priceAmountMicros":10000000,
             "priceCurrencyCode":"EUR","recurrenceMode":1,"billingCycleCount":0}]}]}
    """)

    // SDK fixtures use its real JSON parser and builder, including empty-token validation.
    private fun product(json: String): ProductDetails =
        ProductDetails::class.java.getDeclaredConstructor(String::class.java).let {
            it.isAccessible = true
            it.newInstance(json.trimIndent())
        }

    private fun checkoutProduct(params: BillingFlowParams) =
        params.zzk().single() as BillingFlowParams.ProductDetailsParams

    private fun subscriptionUpdate(params: BillingFlowParams): Any? =
        BillingFlowParams::class.java.declaredFields.single {
            it.type == BillingFlowParams.SubscriptionUpdateParams::class.java
        }.let {
            it.isAccessible = true
            it.get(params)
        }.let { update ->
            // The SDK may create an empty update object. Only nonempty old tokens count.
            if (update == null) null else {
                val tokenField = update.javaClass.declaredFields.first { it.type == String::class.java }
                tokenField.isAccessible = true
                update.takeIf { !((tokenField.get(update) as? String).isNullOrEmpty()) }
            }
        }
}
