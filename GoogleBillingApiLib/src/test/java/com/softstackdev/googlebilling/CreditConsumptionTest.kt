package com.softstackdev.googlebilling

import com.softstackdev.googlebilling.typesProductDetails.CreditConsumableAugmentedProductDetails
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class CreditConsumptionTest {
    private class Local : ILocalProductDetails {
        var token = ""
        var credits = 0
        override fun getFree24Timestamp(productName: String) = 0L
        override fun setFreeFor24h(productName: String) = 0L
        override fun isPurchased(productName: String) = false
        override fun saveProductPurchase(productName: String, purchasedPlayStore: Boolean) = Unit
        override fun saveSubscription(productName: String, purchasedPlayStore: Boolean, selectedBillingPeriod: String) = Unit
        override fun getSelectedBillingPeriod(productName: String) = ""
        override fun getPurchaseToken(prefixName: String) = token
        override fun savePurchaseToken(prefixName: String, purchaseToken: String) { token = purchaseToken }
        override fun getCredits(prefixName: String, purchaseToken: String) = credits
        override fun saveCredits(prefixName: String, purchaseToken: String, productCredit: Int) { credits = productCredit }
    }

    @Test fun duplicateAndEmptyTokensCannotCreditAgainIncludingAfterRecreation() {
        val local = Local()
        BillingDependency.localProductDetails = local
        fun product() = CreditConsumableAugmentedProductDetails("credits", "Credits", "", "credits", 25, 1000)
        val original = product()
        original.addCreditOnePurchase("first")
        original.addCreditOnePurchase("first")
        original.addCreditOnePurchase("")
        assertEquals(25, original.creditNos)
        val restored = product()
        restored.addCreditOnePurchase("first")
        assertEquals(25, restored.creditNos)
        restored.addCreditOnePurchase("second")
        assertEquals(50, restored.creditNos)
        assertEquals(50, local.credits)
    }
}
