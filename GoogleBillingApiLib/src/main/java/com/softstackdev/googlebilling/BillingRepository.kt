package com.softstackdev.googlebilling

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import com.softstackdev.googlebilling.AugmentedProductDetailsDao.updateCreditOnConsumed
import com.softstackdev.googlebilling.BillingRepository.RetryPolicies.connectionRetryPolicy
import com.softstackdev.googlebilling.BillingRepository.RetryPolicies.resetConnectionRetryPolicyCounter
import com.softstackdev.googlebilling.BillingRepository.RetryPolicies.taskExecutionRetryPolicy
import com.softstackdev.googlebilling.Products.CONSUMABLE_PRODUCTS
import com.softstackdev.googlebilling.Products.INAPP_PRODUCTS
import com.softstackdev.googlebilling.Products.STORE_APP_PRODUCTS
import com.softstackdev.googlebilling.Products.SUBSCRIPTION_PRODUCTS
import com.softstackdev.googlebilling.Products.getQueryProductList
import com.softstackdev.googlebilling.typesProductDetails.AugmentedProductDetails
import com.softstackdev.googlebilling.typesProductDetails.CreditConsumableAugmentedProductDetails
import com.softstackdev.googlebilling.typesProductDetails.InstantConsumableProductDetails
import com.softstackdev.googlebilling.typesProductDetails.StoreAppAugmentedProductDetails
import com.softstackdev.googlebilling.utils.openPlayStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

/**
 * Created by Nena_Schmidt on 05.03.2019.
 */
class BillingRepository private constructor(private var application: Application) :
    PurchasesUpdatedListener, BillingClientStateListener,
    ProductDetailsResponseListener, ConsumeResponseListener {

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
        const val TAG = "SSDBillingRepository"
    }


    private lateinit var playStoreBillingClient: BillingClient

    private var playStoreResponseCount: Byte = 0
    private var playStoreResponseCountExpected: Byte = 0

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
            try {
                playStoreBillingClient.startConnection(this)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                Log.e(
                    TAG,
                    "IllegalStateException Failed to connect to billing client: ${e.message}"
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
                Log.e(TAG, "SecurityException Failed to connect to billing client: ${e.message}")
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.e(TAG, "Billing Service Disconnected")
        connectionRetryPolicy { connectToPlayBillingService() }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                resetConnectionRetryPolicyCounter()//for retry policy
                playStoreResponseCount = 0
                playStoreResponseCountExpected = 0

                queryProductDetailsAsync(INAPP_PRODUCTS)
                queryProductDetailsAsync(SUBSCRIPTION_PRODUCTS)
                queryProductDetailsAsync(getQueryProductList(CONSUMABLE_PRODUCTS))
                queryProductDetailsAsync(STORE_APP_PRODUCTS)

                queryPurchasesAsync()
            }

            else -> {
                Log.e(TAG, "onBillingSetupFinished - ${billingResult.debugMessage}")
            }
        }
    }

    private fun queryProductDetailsAsync(
        productList: List<QueryProductDetailsParams.Product>
    ) {
        if (productList.isEmpty()) {
            return
        }

        playStoreResponseCountExpected++
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        taskExecutionRetryPolicy(playStoreBillingClient, this) {
            playStoreBillingClient.queryProductDetailsAsync(params, this)
        }
    }

    override fun onProductDetailsResponse(
        billingResult: BillingResult,
        productDetails: MutableList<ProductDetails>
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                productDetails.let {
                    getCoroutineScope().launch {
                        AugmentedProductDetailsDao.updateDetails(it)
                        postResponseReceived()
                    }
                }
            }

            else -> {
                Log.e(TAG, "onProductDetailsResponse - ${billingResult.debugMessage}")
            }
        }
    }

    private fun queryPurchasesAsync() {
        playStoreResponseCountExpected++
        taskExecutionRetryPolicy(playStoreBillingClient, this) {
            val purchasesResult = mutableListOf<Purchase>()

            getCoroutineScope().launch {
                var result = playStoreBillingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )

                result.purchasesList.apply {
                    purchasesResult.addAll(this)
                }

                if (isSubscriptionSupported()) {
                    result = playStoreBillingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder()
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )

                    result.purchasesList.apply {
                        purchasesResult.addAll(this)
                    }
                }

                AugmentedProductDetailsDao.resetPurchasesForAll()
                postResponseReceived()
                processPurchasesResponseAsync(purchasesResult)
            }
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

            val (consumables, nonConsumables) = validPurchases.partition { purchase ->
                CONSUMABLE_PRODUCTS.map { it.productId }.contains(purchase.products[0])
            }

            acknowledgeNonConsumablePurchasesAsync(nonConsumables)
            handleConsumablePurchasesAsync(consumables)
        }
    }

    private fun handleConsumablePurchasesAsync(consumables: List<Purchase>) {
        consumables.forEach { purchase ->

            AugmentedProductDetailsDao.augmentedProductDetailsList.find {
                it.productId == purchase.products[0]
            }?.apply {
                when (this) {
                    is CreditConsumableAugmentedProductDetails -> {
                        pendingToBeConsumedPurchaseToken = purchase.purchaseToken
                        consumePurchase(purchase.purchaseToken)
                    }

                    is InstantConsumableProductDetails -> consumePurchase(purchase.purchaseToken)
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

        AugmentedProductDetailsDao.updateAcknowledgedPurchases(acknowledgedPurchase)
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        playStoreBillingClient.acknowledgePurchase(params) { billingResult ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    AugmentedProductDetailsDao.updateAcknowledgedPurchase(purchase)
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

    fun makePurchase(
        activity: Activity,
        augmentedProductDetails: AugmentedProductDetails,
        subscriptionOfferId: String = ""
    ): Boolean {
        if (augmentedProductDetails is StoreAppAugmentedProductDetails) {
            openPlayStore(activity, augmentedProductDetails.packageName)
            return true
        }

        val originalProductDetails = augmentedProductDetails.originalProductDetails ?: return false
        val subscriptionOfferToken =
            originalProductDetails.subscriptionOfferDetails?.firstOrNull {
                it.offerId == subscriptionOfferId
            }?.offerToken ?: ""
        val productDetailsParamsList =
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(originalProductDetails)
                    .setOfferToken(subscriptionOfferToken)
                    .build()
            )

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)

        if(augmentedProductDetails.purchaseToken.isNotEmpty()) {
            params.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(augmentedProductDetails.purchaseToken)
                    .setSubscriptionReplacementMode(
                        BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE
                    )
                    .build()
            )
        }

        taskExecutionRetryPolicy(playStoreBillingClient, this) {
            playStoreBillingClient.launchBillingFlow(activity, params.build())
        }
        return true
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
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
        return if (verifyPurchase(
                BillingDependency.base64EncodedPublicKey,
                purchase.originalJson,
                purchase.signature
            )
        ) {
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
        val billingResult =
            playStoreBillingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
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

        private const val maxRetry = 5
        private const val baseDelayMillis = 500
        private const val taskDelay = 2000L
        private var retryCounter = AtomicInteger(1)

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
        fun taskExecutionRetryPolicy(
            billingClient: BillingClient,
            listener: BillingRepository,
            task: () -> Unit
        ) {
            val scope = CoroutineScope(Job() + Dispatchers.IO)
            scope.launch {
                if (!billingClient.isReady) {
                    try {
                        billingClient.startConnection(listener)
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()
                        Log.e(
                            TAG,
                            "IllegalStateException on RetryPolicy Failed to connect to billing client: ${e.message}"
                        )
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                        Log.e(
                            TAG,
                            "SecurityException on RetryPolicy Failed to connect to billing client: ${e.message}"
                        )
                    }
                    delay(taskDelay)
                }
                task()
            }
        }
    }

    init {
        instantiateAndConnectToPlayBillingService()
    }
}
