package com.softstackdev.googlebilling

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProxyBillingActivity

/** Compatibility boundary for the pinned Billing Client 9.1.0 checkout activity. */
class CompatibleProxyBillingActivity : ProxyBillingActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
        } catch (error: NoSuchMethodError) {
            if (!isMissingBackgroundStartMode(error)) throw error
            // Google has already initialized its receiver, request code, transaction
            // metadata and cancellation state. Retry only the pending-intent launch;
            // keep Google's result handling and saved-state lifecycle unchanged.
            Log.w(TAG, "Framework lacks billing background-start options; using legacy launch")
            launchWithoutBackgroundStartOptions()
        }
    }

    private fun launchWithoutBackgroundStartOptions() {
        val inAppMessage = !intent.hasExtra("BUY_INTENT") && intent.hasExtra("IN_APP_MESSAGE_INTENT")
        val requestCode = when {
            inAppMessage -> 101
            intent.getBooleanExtra("IS_FLOW_FROM_FIRST_PARTY_CLIENT", false) -> 110
            else -> 100
        }
        // Use the legacy overload: this framework may lack APIs despite its reported SDK version.
        @Suppress("DEPRECATION")
        val pendingIntent = intent.getParcelableExtra<PendingIntent>(
            if (inAppMessage) "IN_APP_MESSAGE_INTENT" else "BUY_INTENT"
        )
        if (pendingIntent == null) {
            Log.w(TAG, "Cannot retry billing launch: pending intent is missing")
            reportLaunchFailure(requestCode)
            return
        }
        try {
            startIntentSenderForResult(pendingIntent.intentSender, requestCode, Intent(), 0, 0, 0, null)
        } catch (error: IntentSender.SendIntentException) {
            Log.w(TAG, "Billing pending intent could not launch", error)
            reportLaunchFailure(requestCode)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Framework rejected billing pending intent", error)
            reportLaunchFailure(requestCode)
        }
    }

    private fun reportLaunchFailure(requestCode: Int) {
        // Deliver failure through Google's normal callback rather than granting an
        // entitlement or leaving checkout hanging. In-app messages use result 0.
        val data = if (requestCode == 101) null else {
            Intent().putExtra("RESPONSE_CODE", BillingClient.BillingResponseCode.ERROR)
                .putExtra("DEBUG_MESSAGE", "Unable to launch Google Play checkout")
        }
        onActivityResult(requestCode, Activity.RESULT_CANCELED, data)
    }

    internal companion object {
        private const val TAG = "CompatibleBilling"
        fun isMissingBackgroundStartMode(error: NoSuchMethodError): Boolean =
            error.message.orEmpty().let {
                it.contains("ActivityOptions") &&
                    it.contains("setPendingIntentBackgroundActivityStartMode")
            }
    }
}
