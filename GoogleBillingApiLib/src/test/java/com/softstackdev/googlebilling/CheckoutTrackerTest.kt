package com.softstackdev.googlebilling

import org.junit.Assert.*
import org.junit.Test

class CheckoutTrackerTest {
    private val events = mutableListOf<CheckoutEvent>()
    private val tracker = CheckoutTracker(now = { 1000L })
    private fun begin(id: String = "pro") = tracker.begin(id, "p1m") { events += it }!!

    @Test fun successRequiresVerificationAndDeliveryAndIsNotReplayed() {
        val attempt = begin()
        tracker.launchResult(attempt, 0)
        assertSame(attempt, tracker.observe(listOf("pro"), "new", false, 1001L))
        tracker.verified(attempt)
        assertEquals(CheckoutStatus.VERIFIED, events.last().status)
        repeat(2) { tracker.delivered("new", CheckoutStage.ACKNOWLEDGEMENT) }
        assertEquals(1, events.count { it.status == CheckoutStatus.COMPLETED })
        assertNull(tracker.observe(listOf("pro"), "new", false, 1001L))
    }

    @Test fun restoreQueriesNeverAttachAnUnknownTokenToCheckout() {
        begin()
        assertNull(tracker.observe(listOf("pro"), "restored", true, 1001L))
        tracker.delivered("restored", CheckoutStage.ACKNOWLEDGEMENT)
        assertTrue(events.isEmpty())
        assertNull(tracker.observe(listOf("pro"), "restored", false, 1001L))
        assertTrue(events.isEmpty())
    }

    @Test fun pendingPaymentCanCompleteThroughARecoveryQueryWithoutStealingAnotherCheckout() {
        val old = begin()
        tracker.observe(listOf("pro"), "pending", false, 1001L)
        repeat(2) { tracker.pending(old) }
        assertEquals(1, events.count { it.status == CheckoutStatus.PENDING })
        val newer = begin("credits")
        assertSame(old, tracker.observe(listOf("pro"), "pending", true, 1001L))
        tracker.verified(old)
        tracker.delivered("pending", CheckoutStage.ACKNOWLEDGEMENT)
        assertEquals(old.id, events.last().requestId)
        tracker.purchaseResponseFailure(1)
        assertEquals(newer.id, events.last().requestId)
        assertEquals(CheckoutStatus.CANCELLED, events.last().status)
    }

    @Test fun cancellationAndLaunchErrorsHaveDistinctStagesAndAllowRetry() {
        tracker.launchResult(begin(), 2)
        assertEquals(CheckoutStatus.FAILED, events.last().status)
        assertEquals(CheckoutStage.LAUNCH, events.last().stage)
        val retried = begin()
        tracker.launchResult(retried, 0)
        tracker.purchaseResponseFailure(1)
        assertEquals(CheckoutStatus.CANCELLED, events.last().status)
        assertEquals(CheckoutStage.PURCHASE, events.last().stage)
        assertNotNull(begin())
    }

    @Test fun rapidSecondTapDoesNotReplaceFirstCheckout() {
        val first = begin()
        assertNull(tracker.begin("credits", "") { events += it })
        assertEquals(CheckoutReason.CHECKOUT_IN_PROGRESS, events.last().reason)
        tracker.purchaseResponseFailure(1)
        assertEquals(first.id, events.last().requestId)
    }

    @Test fun unrelatedProductsAndKnownOldTokensCannotBeNewSales() {
        val attempt = begin()
        tracker.remember("old")
        assertNull(tracker.observe(listOf("other"), "elsewhere", false, 1001L))
        assertTrue(events.isEmpty())
        assertNull(tracker.observe(listOf("pro"), "old", false, 1001L))
        assertTrue(events.isEmpty())
        assertSame(attempt, tracker.observe(listOf("pro"), "fresh", false, 1001L))
        tracker.verified(attempt)
        tracker.delivered("fresh", CheckoutStage.ACKNOWLEDGEMENT)
        assertEquals(CheckoutStatus.COMPLETED, events.last().status)
    }

    @Test fun failedAcknowledgementCanRecoverWithoutAnotherPurchaseEvent() {
        val attempt = begin()
        tracker.observe(listOf("pro"), "paid", false, 1001L)
        tracker.verified(attempt)
        tracker.processingFailed("paid", CheckoutStage.ACKNOWLEDGEMENT, 2)
        assertEquals(CheckoutStatus.PROCESSING_FAILED, events.last().status)
        assertSame(attempt, tracker.observe(listOf("pro"), "paid", true, 1001L))
        tracker.verified(attempt)
        tracker.delivered("paid", CheckoutStage.ACKNOWLEDGEMENT)
        assertEquals(1, events.count { it.status == CheckoutStatus.VERIFIED })
        assertEquals(1, events.count { it.status == CheckoutStatus.COMPLETED })
    }

    @Test fun invalidSignatureAndEmptyResponsesDoNotCreateCompletion() {
        val attempt = begin()
        tracker.observe(listOf("pro"), "invalid", false, 1001L)
        tracker.invalid(attempt, CheckoutReason.INVALID_SIGNATURE)
        tracker.delivered("invalid", CheckoutStage.ACKNOWLEDGEMENT)
        assertEquals(CheckoutStatus.FAILED, events.last().status)
        begin()
        tracker.emptyResponse()
        assertEquals(CheckoutStatus.UNRESOLVED, events.last().status)
        assertFalse(events.any { it.status == CheckoutStatus.COMPLETED })
    }

    @Test fun listenersCanFailOrRetryWithoutBlockingBilling() {
        val failing = tracker.begin("pro", "p1m") { error("subscriber failed") }!!
        tracker.reject(failing, CheckoutReason.OFFER_UNAVAILABLE)
        var retry: CheckoutTracker.Attempt? = null
        val first = tracker.begin("pro", "p1m") { retry = begin() }!!
        tracker.reject(first, CheckoutReason.PRODUCT_UNAVAILABLE)
        assertNotNull(retry)
    }

    @Test fun processRestartRestoreDoesNotRecreatePurchaseAttribution() {
        val restarted = CheckoutTracker()
        assertNull(restarted.observe(listOf("pro"), "paid-before-restart", true, 1001L))
        restarted.delivered("paid-before-restart", CheckoutStage.ACKNOWLEDGEMENT)
        assertTrue(events.isEmpty())
    }

    @Test fun retainedPendingCallbacksExpireWithoutInventingCancellation() {
        var clock = 0L
        val expiring = CheckoutTracker(now = { clock })
        val attempt = expiring.begin("pro", "p1m") { events += it }!!
        expiring.observe(listOf("pro"), "pending", false, 1001L)
        expiring.pending(attempt)
        clock = 8 * 24 * 60 * 60 * 1000L
        expiring.begin("pro", "p1m") {}
        assertEquals(CheckoutStatus.UNRESOLVED, events.last().status)
        assertEquals(CheckoutReason.TRACKING_EXPIRED, events.last().reason)
    }

    @Test fun missingPlayResultCannotBlockCheckoutIndefinitely() {
        var clock = 0L
        val expiring = CheckoutTracker(now = { clock })
        val old = expiring.begin("pro", "p1m") { events += it }!!
        expiring.launchResult(old, 0)
        clock = 6 * 60 * 1000L
        assertNotNull(expiring.begin("pro", "p1y") {})
        assertEquals(CheckoutStatus.UNRESOLVED, events.last().status)
    }

    @Test fun explicitAlreadyOwnedTriggersNoCompletedSaleFromTheRestore() {
        val attempt = begin()
        tracker.launchResult(attempt, 7)
        assertEquals(CheckoutStatus.ALREADY_OWNED, events.single().status)
        assertNull(tracker.observe(listOf("pro"), "old", true, 1001L))
        tracker.delivered("old", CheckoutStage.ACKNOWLEDGEMENT)
        assertEquals(1, events.size)
    }
    @Test fun delayedOlderPurchaseCannotAdoptANewAttemptForTheSameProduct() {
        var clock = 1000L
        val delayed = CheckoutTracker(now = { clock })
        delayed.begin("pro", "p1m") { events += it }
        clock += 6 * 60 * 1000L
        val retry = delayed.begin("pro", "p1y") { events += it }!!
        assertNull(delayed.observe(listOf("pro"), "older-token", false, 2000L))
        assertSame(retry, delayed.observe(listOf("pro"), "new-token", false, clock + 1))
        delayed.verified(retry)
        delayed.delivered("new-token", CheckoutStage.ACKNOWLEDGEMENT)
        assertEquals("p1y", events.last().billingPeriod)
        assertEquals(1, events.count { it.status == CheckoutStatus.COMPLETED })
    }

}
