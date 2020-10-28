package com.softstackdev.googlebilling

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*
import com.softstackdev.googlebilling.BillingRepository.RetryPolicies.connectionRetryPolicy
import com.softstackdev.googlebilling.BillingRepository.RetryPolicies.resetConnectionRetryPolicyCounter
import com.softstackdev.googlebilling.BillingRepository.RetryPolicies.taskExecutionRetryPolicy
import com.softstackdev.googlebilling.typesSkuDetails.AugmentedSkuDetails
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

/**
 * Created by Nena_Schmidt on 05.03.2019.
 */
class BillingRepository : PurchasesUpdatedListener, BillingClientStateListener, SkuDetailsResponseListener {

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
    private var playStoreResponseCountExpected: Byte = 3

    private val TAG = "BillingRepository"

    private fun getCoroutineScope() = CoroutineScope(Job() + Dispatchers.IO)

    private fun instantiateAndConnectToPlayBillingService() {
        playStoreBillingClient = BillingClient
                .newBuilder(application.applicationContext).setListener(this).build()

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
    override fun onBillingSetupFinished(responseCode: Int) {
        when (responseCode) {
            BillingClient.BillingResponse.OK -> {
                resetConnectionRetryPolicyCounter()//for retry policy
                playStoreResponseCount = 0
                querySkuDetailsAsync(BillingClient.SkuType.INAPP, SkuProductId.INAPP_SKUS)
                querySkuDetailsAsync(BillingClient.SkuType.SUBS, SkuProductId.SUBS_SKUS)

                queryPurchasesAsync()
            }
            BillingClient.BillingResponse.BILLING_UNAVAILABLE -> {
                Log.e(TAG, "BillingClient.BillingResponse.BILLING_UNAVAILABLE")
            }
        }
    }

    private fun querySkuDetailsAsync(type: String, skuList: MutableList<String>) {
        val params = SkuDetailsParams.newBuilder()
        params.setType(type)
        params.setSkusList(skuList)
        taskExecutionRetryPolicy(playStoreBillingClient, this) {
            playStoreBillingClient.querySkuDetailsAsync(params.build(), this)
        }
    }

    override fun onSkuDetailsResponse(responseCode: Int, skuDetailsList: MutableList<SkuDetails>?) {
        if (responseCode == BillingClient.BillingResponse.OK) {
            skuDetailsList?.let {
                getCoroutineScope().launch {
                    AugmentedSkuDetailsDao.updateDetails(it)
                    postResponseReceived()
                }
            }
        } else {
            Log.e(TAG, "SkuDetails query failed with response: $responseCode")
        }
    }

    private fun queryPurchasesAsync() {
        taskExecutionRetryPolicy(playStoreBillingClient, this) {
            val purchasesResult = mutableListOf<Purchase>()

            var result = playStoreBillingClient.queryPurchases(BillingClient.SkuType.INAPP)
            result?.purchasesList?.apply {
                this.forEach {
                    if (isSignatureValid(it)) {
                        purchasesResult.add(it)
                    }
                }
            }

            result = playStoreBillingClient.queryPurchases(BillingClient.SkuType.SUBS)
            result?.purchasesList?.apply {
                this.forEach {
                    if (isSignatureValid(it)) {
                        purchasesResult.add(it)
                    }
                }
            }

            AugmentedSkuDetailsDao.updatePurchasesForAll(purchasesResult)
            postResponseReceived()
        }
    }

    private fun postResponseReceived() {
        playStoreResponseCount++
        if (playStoreResponseCount == playStoreResponseCountExpected) {
            playStoreLoaded.postValue(true)
        }
    }

    fun makePurchase(activity: Activity, augmentedSkuDetails: AugmentedSkuDetails) {
        val skuDetails = SkuDetails(augmentedSkuDetails.originalJson)
        val params = BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails)
                .build()

        taskExecutionRetryPolicy(playStoreBillingClient, this) {
            playStoreBillingClient.launchBillingFlow(activity, params)
        }
    }

    @SuppressLint("SwitchIntDef")
    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
        when (responseCode) {
            BillingClient.BillingResponse.OK -> {
                purchases?.forEach { purchase ->
                    if (isSignatureValid(purchase)) {
                        AugmentedSkuDetailsDao.updateNewPurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponse.ITEM_ALREADY_OWNED -> {
                //item already owned? call queryPurchasesAsync to verify and process all such items
                queryPurchasesAsync()
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
