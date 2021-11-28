package com.softstackdev.googlebilling

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*
import com.softstackdev.googlebilling.AugmentedSkuDetailsDao.updateCreditOnConsumed
import com.softstackdev.googlebilling.BillingRepository.RetryPolicies.connectionRetryPolicy
import com.softstackdev.googlebilling.BillingRepository.RetryPolicies.resetConnectionRetryPolicyCounter
import com.softstackdev.googlebilling.BillingRepository.RetryPolicies.taskExecutionRetryPolicy
import com.softstackdev.googlebilling.SkuProductId.CONSUMABLE_SKUS
import com.softstackdev.googlebilling.typesSkuDetails.AugmentedSkuDetails
import com.softstackdev.googlebilling.typesSkuDetails.CreditConsumableAugmentedSkuDetails
import com.softstackdev.googlebilling.typesSkuDetails.InstantConsumableSkuDetails
import com.softstackdev.googlebilling.typesSkuDetails.StoreAppAugmentedSkuDetails
import com.softstackdev.googlebilling.utils.openPlayStore
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

/**
 * Created by Nena_Schmidt on 05.03.2019.
 */
class BillingRepository : PurchasesUpdatedListener, BillingClientStateListener,
        SkuDetailsResponseListener, ConsumeResponseListener {

    companion object {

        @Volatile
        private var instance: BillingRepository? = null

        fun getInstance(application: Application): BillingRepository =
                instance ?: synchronized(this) {
                    instance ?: BillingRepository(application).also {
                        instance = it
                    }
                }

        @Volatile
        var playStoreLoaded: MutableLiveData<Boolean> = MutableLiveData()
    }

    private constructor(application: Application) {
        this.application = application
        instantiateAndConnectToPlayBillingService()
    }


    private lateinit var playStoreBillingClient: BillingClient
    private var application: Application

    private var playStoreResponseCount: Byte = 0
    private var playStoreResponseCountExpected: Byte = 0

    private val TAG = "BillingRepository"

    private fun getCoroutineScope() = CoroutineScope(Job() + Dispatchers.IO)

    private fun instantiateAndConnectToPlayBillingService() {
        playStoreBillingClient = BillingClient
                .newBuilder(application.applicationContext)
                .enablePendingPurchases() // since v2.0 required or app will crash
                .setListener(this).build()

        connectToPlayBillingService()
    }

    private fun connectToPlayBillingService() {
        if (!playStoreBillingClient.isReady) {
            playStoreBillingClient.startConnection(this)
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.e(TAG, "Billing Service Disconnected")
        connectionRetryPolicy { connectToPlayBillingService() }
    }

    @SuppressLint("SwitchIntDef")
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                resetConnectionRetryPolicyCounter()//for retry policy
                playStoreResponseCount = 0
                playStoreResponseCountExpected = 0

                querySkuDetailsAsync(BillingClient.SkuType.INAPP, SkuProductId.INAPP_SKUS)
                querySkuDetailsAsync(BillingClient.SkuType.SUBS, SkuProductId.SUBSCRIPTION_SKUS)
                querySkuDetailsAsync(BillingClient.SkuType.INAPP, SkuProductId.CONSUMABLE_SKUS)
                querySkuDetailsAsync(BillingClient.SkuType.INAPP, SkuProductId.STORE_APP_SKUS)

                queryPurchasesAsync()
            }
            else -> {
                Log.e(TAG, "onBillingSetupFinished - ${billingResult.debugMessage}")
            }
        }
    }

    private fun querySkuDetailsAsync(@BillingClient.SkuType type: String, skuList: MutableList<String>) {
        if (skuList.isEmpty()) {
            return
        }

        playStoreResponseCountExpected++
        val params = SkuDetailsParams.newBuilder()
                .setType(type)
                .setSkusList(skuList)
                .build()
        taskExecutionRetryPolicy(playStoreBillingClient, this) {
            playStoreBillingClient.querySkuDetailsAsync(params, this)
        }
    }

    override fun onSkuDetailsResponse(billingResult: BillingResult, skuDetailsList: MutableList<SkuDetails>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                skuDetailsList?.let {
                    getCoroutineScope().launch {
                        AugmentedSkuDetailsDao.updateDetails(it)
                        postResponseReceived()
                    }
                }
            }
            else -> {
                Log.e(TAG, "onSkuDetailsResponse - ${billingResult.debugMessage}")
            }
        }
    }

    private fun queryPurchasesAsync() {
        playStoreResponseCountExpected++
        taskExecutionRetryPolicy(playStoreBillingClient, this) {
            val purchasesResult = mutableListOf<Purchase>()

            var result = playStoreBillingClient.queryPurchases(BillingClient.SkuType.INAPP)
            result?.purchasesList?.apply {
                purchasesResult.addAll(this)
            }

            if (isSubscriptionSupported()) {
                result = playStoreBillingClient.queryPurchases(BillingClient.SkuType.SUBS)
                result?.purchasesList?.apply {
                    purchasesResult.addAll(this)
                }
            }

            AugmentedSkuDetailsDao.resetPurchasesForAll()
            postResponseReceived()

            processPurchasesResponseAsync(purchasesResult)
        }
    }


    private fun processPurchasesResponseAsync(purchasesResult: List<Purchase>) {
        getCoroutineScope().launch {

            val validPurchases = mutableListOf<Purchase>()
            purchasesResult.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (isSignatureValid(purchase)) {
                        validPurchases.add(purchase)
                    }
                }
            }

            val (consumables, nonConsumables) = validPurchases.partition {
                CONSUMABLE_SKUS.contains(it.sku)
            }
            acknowledgeNonConsumablePurchasesAsync(nonConsumables)
            handleConsumablePurchasesAsync(consumables)
        }
    }

    private fun handleConsumablePurchasesAsync(consumables: List<Purchase>) {
        consumables.forEach { purchase ->

            AugmentedSkuDetailsDao.augmentedSkuDetailsList.find { it.skuName == purchase.sku }?.apply {
                when(this){
                    is CreditConsumableAugmentedSkuDetails -> {
                        pendingToBeConsumedPurchaseToken = purchase.purchaseToken
                        consumePurchase(purchase.purchaseToken)
                    }
                    is InstantConsumableSkuDetails -> consumePurchase(purchase.purchaseToken)
                }
            }
        }
    }

    private fun acknowledgeNonConsumablePurchasesAsync(nonConsumables: List<Purchase>) {
        val acknowledgedPurchase = mutableListOf<Purchase>()
        nonConsumables.forEach { purchase ->
            if (purchase.isAcknowledged) {
                acknowledgedPurchase.add(purchase)
            } else {
                acknowledgePurchase(purchase)
            }
        }

        AugmentedSkuDetailsDao.updateAcknowledgedPurchases(acknowledgedPurchase)
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

        playStoreBillingClient.acknowledgePurchase(params) { billingResult ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    AugmentedSkuDetailsDao.updateAcknowledgedPurchase(purchase)
                }
                else -> Log.d(TAG, "onAcknowledgePurchaseResponse - ${billingResult.debugMessage}")
            }
        }
    }

    private fun postResponseReceived() {
        playStoreResponseCount++
        if (playStoreResponseCount == playStoreResponseCountExpected) {
            playStoreLoaded.postValue(true)
        }
    }

    fun makePurchase(activity: Activity, augmentedSkuDetails: AugmentedSkuDetails) {
        if (augmentedSkuDetails is StoreAppAugmentedSkuDetails) {
            openPlayStore(activity, augmentedSkuDetails.packageName)
            return
        }

        val skuDetails = SkuDetails(augmentedSkuDetails.originalJson)
        val params = BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails)
                .build()

        taskExecutionRetryPolicy(playStoreBillingClient, this) {
            playStoreBillingClient.launchBillingFlow(activity, params)
        }
    }

    @SuppressLint("SwitchIntDef")
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.apply {
                    processPurchasesResponseAsync(purchases)
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                //item already owned? call queryPurchasesAsync to verify and process all such items
                queryPurchasesAsync()
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                connectToPlayBillingService()
            }
            else -> {
                Log.e(TAG, "onPurchasesUpdated - ${billingResult.debugMessage}")
            }
        }
    }

    private fun consumePurchase(purchaseToken: String) {
        val params = ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
        playStoreBillingClient.consumeAsync(params, this)
    }

    override fun onConsumeResponse(billingResult: BillingResult, purchaseToken: String) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                getCoroutineScope().launch {
                    updateCreditOnConsumed(purchaseToken)
                }
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                connectToPlayBillingService()
            }
            else -> {
                Log.e(TAG, "onConsumeResponse - ${billingResult.debugMessage}")
            }
        }
    }

    private fun isSignatureValid(purchase: Purchase): Boolean {
        return if (verifyPurchase(BillingDependency.base64EncodedPublicKey, purchase.originalJson, purchase.signature)) {
            true
        } else {
            Log.e(TAG, "Invalid signature - purchase= $purchase")
            false
        }
    }

    /**
     * Checks if the user's device supports subscriptions
     *
     * Some Android phones might have an older version of the Google Play Store app
     * that does not support certain product types, such as subscriptions
     */
    private fun isSubscriptionSupported(): Boolean {
        val billingResult = playStoreBillingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        var succeeded = false
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> succeeded = true
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> connectToPlayBillingService()
            else -> Log.e(TAG, "isSubscriptionSupported - ${billingResult.debugMessage}")
        }
        return succeeded
    }

    /**
     * This private object class shows example retry policies. You may choose to replace it with
     * your own policies.
     */
    private object RetryPolicies {

        private val maxRetry = 5
        private var retryCounter = AtomicInteger(1)
        private val baseDelayMillis = 500
        private val taskDelay = 2000L

        fun resetConnectionRetryPolicyCounter() {
            retryCounter.set(1)
        }

        /**
         * This works because it actually makes one call. Then it waits for success or failure.
         * onSuccess it makes no more calls and resets the retryCounter to 1. onFailure another
         * call is made, until too many failures cause retryCounter to reach maxRetry and the
         * policy stops trying. This is a safe algorithm: the initial calls to
         * connectToPlayBillingService from instantiateAndConnectToPlayBillingService is always
         * independent of the RetryPolicies. And so the Retry Policy exists only to help and never
         * to hurt.
         */
        fun connectionRetryPolicy(block: () -> Unit) {
            val scope = CoroutineScope(Job() + Dispatchers.IO)
            scope.launch {
                val counter = retryCounter.getAndIncrement()
                if (counter < maxRetry) {
                    val waitTime: Long = (2f.pow(counter) * baseDelayMillis).toLong()
                    delay(waitTime)
                    block()
                }
            }

        }

        /**
         * All this is doing is check that billingClient is connected and if it's not, request
         * connection, wait x number of seconds and then proceed with the actual task.
         */
        fun taskExecutionRetryPolicy(billingClient: BillingClient, listener: BillingRepository, task: () -> Unit) {
            val scope = CoroutineScope(Job() + Dispatchers.IO)
            scope.launch {
                if (!billingClient.isReady) {
                    billingClient.startConnection(listener)
                    delay(taskDelay)
                }
                task()
            }
        }
    }
}
