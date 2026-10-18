package com.softstackdev.googlebilling

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import com.android.billingclient.api.BillingClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CompatibleProxyBillingActivityTest {
    @Test
    fun sdkComponentResolvesToOurNonExportedActivity() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getActivityInfo(
            ComponentName(app.packageName, "com.android.billingclient.api.ProxyBillingActivity"), 0
        )
        assertEquals(CompatibleProxyBillingActivity::class.java.name, info.targetActivity)
        assertFalse(info.exported)
    }

    @Test
    fun realOlderFrameworkKeepsGoogleLegacyLaunch() {
        val activity = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, checkoutIntent()).create().get()
        assertNull(shadowOf(activity).lastIntentSenderRequest.options)
        assertEquals(100, shadowOf(activity).lastIntentSenderRequest.requestCode)
    }

    @Test
    @Config(sdk = [34])
    fun supportedFrameworkKeepsGoogleBackgroundStartOptions() {
        val activity = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, checkoutIntent()).create().get()
        assertNotNull(shadowOf(activity).lastIntentSenderRequest.options)
        assertEquals(100, shadowOf(activity).lastIntentSenderRequest.requestCode)
    }

    @Test
    fun missingActualFrameworkMethodStillStartsCheckout() {
        // Real API 33 framework with SDK_INT reporting 34 reproduces the exact
        // NoSuchMethodError from Google's compiled activity, without mocking it.
        val controller = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, checkoutIntent())
        reportNewerAndroidVersion()
        val activity = controller.create().get()
        val request = shadowOf(activity).lastIntentSenderRequest
        assertNotNull(request)
        assertNull(request.options)
        assertEquals(100, request.requestCode)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun firstPartyAndInAppMessageFlowsRetainTheirRequestCodes() {
        reportNewerAndroidVersion()
        val firstParty = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java,
            checkoutIntent().putExtra("IS_FLOW_FROM_FIRST_PARTY_CLIENT", true)).create().get()
        assertEquals(110, shadowOf(firstParty).lastIntentSenderRequest.requestCode)
        val message = checkoutIntent()
        message.putExtra("IN_APP_MESSAGE_INTENT", message.getParcelableExtra<PendingIntent>("BUY_INTENT"))
        message.removeExtra("BUY_INTENT")
        val messageActivity = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, message).create().get()
        assertEquals(101, shadowOf(messageActivity).lastIntentSenderRequest.requestCode)
    }

    @Test
    fun successfulResultUsesGooglePurchaseBroadcastAndTransactionMetadata() {
        reportNewerAndroidVersion()
        val activity = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, checkoutIntent()).create().get()
        val data = Intent().putExtra("RESPONSE_CODE", BillingClient.BillingResponseCode.OK)
            .putStringArrayListExtra("INAPP_PURCHASE_DATA_LIST", arrayListOf("purchase-json"))
        shadowOf(activity).callOnActivityResult(100, Activity.RESULT_OK, data)
        val broadcast = purchaseBroadcast()
        assertEquals(0, broadcast.getIntExtra("RESPONSE_CODE", -1))
        assertEquals(arrayListOf("purchase-json"), broadcast.getStringArrayListExtra("INAPP_PURCHASE_DATA_LIST"))
        assertEquals(123L, broadcast.getLongExtra("billingClientTransactionId", -1L))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun cancellationUsesGoogleCallbackAndFinishesCheckout() {
        reportNewerAndroidVersion()
        val activity = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, checkoutIntent()).create().get()
        shadowOf(activity).callOnActivityResult(100, Activity.RESULT_CANCELED,
            Intent().putExtra("RESPONSE_CODE", BillingClient.BillingResponseCode.USER_CANCELED))
        assertEquals(1, purchaseBroadcast().getIntExtra("RESPONSE_CODE", -1))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun rejectedPendingIntentReportsFailureInsteadOfCrashing() {
        val controller = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, checkoutIntent())
        shadowOf(controller.get()).setThrowIntentSenderException(true)
        reportNewerAndroidVersion()
        val activity = controller.create().get()
        assertEquals(6, purchaseBroadcast().getIntExtra("RESPONSE_CODE", -1))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun restoredActivityDoesNotStartCheckoutAgain() {
        reportNewerAndroidVersion()
        val original = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, checkoutIntent()).create()
        val state = Bundle()
        original.saveInstanceState(state).destroy()
        val restored = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, checkoutIntent()).create(state).get()
        assertNull(shadowOf(restored).lastIntentSenderRequest)
        assertEquals(123L, state.getLong("billingClientTransactionId"))
    }

    @Test
    fun missingCheckoutIntentReportsOneFailureAndPreservesMetadata() {
        val invalid = checkoutIntent().apply { removeExtra("BUY_INTENT") }
            .putExtra("wasServiceAutoReconnected", true)
        val controller = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, invalid)
        val activity = controller.create().get()
        assertNull(shadowOf(activity).lastIntentSenderRequest)
        assertTrue(activity.isFinishing)
        val result = purchaseBroadcast()
        assertEquals(BillingClient.BillingResponseCode.ERROR, result.getIntExtra("RESPONSE_CODE", -1))
        assertEquals(123L, result.getLongExtra("billingClientTransactionId", -1))
        assertTrue(result.getBooleanExtra("wasServiceAutoReconnected", false))
        controller.destroy()
        assertEquals(1, shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents.count {
            it.action == "com.android.vending.billing.LOCAL_BROADCAST_PURCHASES_UPDATED"
        })
        // A failed launch must not prevent the next valid checkout.
        val retry = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, checkoutIntent()).create().get()
        assertNotNull(shadowOf(retry).lastIntentSenderRequest)
        assertFalse(retry.isFinishing)
    }

    @Test
    fun nullFirstPartyCheckoutReportsErrorWithFirstPartyMarker() {
        val invalid = checkoutIntent().putExtra("BUY_INTENT", null as PendingIntent?)
            .putExtra("IS_FLOW_FROM_FIRST_PARTY_CLIENT", true)
        val activity = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, invalid).create().get()
        assertTrue(activity.isFinishing)
        assertEquals(BillingClient.BillingResponseCode.ERROR, purchaseBroadcast().getIntExtra("RESPONSE_CODE", -1))
        assertTrue(purchaseBroadcast().getBooleanExtra("IS_FIRST_PARTY_PURCHASE", false))
    }

    @Test
    fun nullInAppMessageNotifiesItsReceiverWithoutPurchaseBroadcast() {
        val results = mutableListOf<Int>()
        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) { results += resultCode }
        }
        val invalid = checkoutIntent().apply { removeExtra("BUY_INTENT") }
            .putExtra("IN_APP_MESSAGE_INTENT", null as PendingIntent?)
            .putExtra("in_app_message_result_receiver", receiver)
        val controller = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, invalid)
        val activity = controller.create().get()
        assertTrue(activity.isFinishing)
        controller.destroy()
        assertEquals(listOf(0), results)
        assertFalse(shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents.any {
            it.action == "com.android.vending.billing.LOCAL_BROADCAST_PURCHASES_UPDATED"
        })
    }

    @Test
    fun recreationWithoutLaunchIntentWaitsForExistingCheckoutResult() {
        val original = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java, checkoutIntent()).create()
        val state = Bundle()
        original.saveInstanceState(state).destroy()
        val restored = Robolectric.buildActivity(CompatibleProxyBillingActivity::class.java,
            Intent(RuntimeEnvironment.getApplication(), CompatibleProxyBillingActivity::class.java)).create(state).get()
        assertFalse(restored.isFinishing)
        assertNull(shadowOf(restored).lastIntentSenderRequest)
        shadowOf(restored).callOnActivityResult(100, Activity.RESULT_OK,
            Intent().putExtra("RESPONSE_CODE", BillingClient.BillingResponseCode.OK))
        assertEquals(BillingClient.BillingResponseCode.OK, purchaseBroadcast().getIntExtra("RESPONSE_CODE", -1))
        assertEquals(123L, purchaseBroadcast().getLongExtra("billingClientTransactionId", -1))
    }

    @Test
    fun unrelatedMissingMethodsAreNotHandled() {
        assertFalse(CompatibleProxyBillingActivity.isMissingBackgroundStartMode(
            NoSuchMethodError("android.app.ActivityOptions.someOtherMethod")))
        assertFalse(CompatibleProxyBillingActivity.isMissingBackgroundStartMode(
            NoSuchMethodError("SomeOtherClass.setPendingIntentBackgroundActivityStartMode")))
    }

    @Suppress("DEPRECATION")
    private fun checkoutIntent(): Intent {
        val app = RuntimeEnvironment.getApplication()
        val pending = PendingIntent.getActivity(app, 0, Intent("test.checkout").setPackage(app.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Intent(app, CompatibleProxyBillingActivity::class.java)
            .putExtra("BUY_INTENT", pending).putExtra("billingClientTransactionId", 123L)
    }

    private fun reportNewerAndroidVersion() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)
    }

    private fun purchaseBroadcast(): Intent = shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents
        .last { it.action == "com.android.vending.billing.LOCAL_BROADCAST_PURCHASES_UPDATED" }
}
