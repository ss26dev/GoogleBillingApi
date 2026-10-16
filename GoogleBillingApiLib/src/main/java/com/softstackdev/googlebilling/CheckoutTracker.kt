package com.softstackdev.googlebilling

/** Process-scoped attribution. Restore queries can finish a known token, never create a checkout. */
internal class CheckoutTracker(
    private val now: () -> Long = System::currentTimeMillis,
    private val dispatch: ((CheckoutEvent) -> Unit, CheckoutEvent) -> Unit = { listener, event -> listener(event) }
) {
    internal class Attempt(
        val id: Long,
        val productId: String,
        val period: String,
        val createdAt: Long,
        val listener: (CheckoutEvent) -> Unit
    ) {
        var terminal = false
        val emitted = mutableSetOf<Triple<CheckoutStatus, CheckoutStage, Int?>>()
    }

    private var sequence = 0L
    private var active: Attempt? = null
    private val tokens = mutableMapOf<String, Attempt>()
    private val knownTokens = linkedSetOf<String>()

    @Synchronized
    fun begin(productId: String, period: String, listener: (CheckoutEvent) -> Unit): Attempt? {
        expire()
        val attempt = Attempt(++sequence, productId, period, now(), listener)
        if (active != null) {
            finish(attempt, CheckoutStatus.FAILED, CheckoutStage.LAUNCH,
                reason = CheckoutReason.CHECKOUT_IN_PROGRESS)
            return null
        }
        active = attempt
        return attempt
    }

    @Synchronized
    fun launchResult(attempt: Attempt, code: Int) {
        if (code == 0) emit(attempt, CheckoutStatus.LAUNCHED, CheckoutStage.LAUNCH, code)
        else responseFailure(attempt, code, CheckoutStage.LAUNCH)
    }

    @Synchronized
    fun reject(attempt: Attempt, reason: CheckoutReason) {
        finish(attempt, CheckoutStatus.FAILED, CheckoutStage.LAUNCH, reason = reason)
    }

    @Synchronized
    fun unresolved(attempt: Attempt, reason: CheckoutReason) {
        finish(attempt, CheckoutStatus.UNRESOLVED, CheckoutStage.PURCHASE, reason = reason)
    }

    @Synchronized
    fun purchaseResponseFailure(code: Int) {
        active?.let { responseFailure(it, code, CheckoutStage.PURCHASE) }
    }

    @Synchronized
    fun emptyResponse() {
        active?.let { unresolved(it, CheckoutReason.EMPTY_PURCHASES) }
    }

    /** Only matching Play purchase callbacks bind new tokens. No token appears in public events. */
    @Synchronized
    fun observe(products: List<String>, token: String, fromRestore: Boolean, purchaseTime: Long): Attempt? {
        expire()
        tokens[token]?.let { return it }
        val attempt = active?.takeIf { it.productId in products }
        if (fromRestore || token.isBlank()) {
            remember(token)
            return null
        }
        if (token in knownTokens) {
            // A delayed duplicate for the same product must not terminate a newer checkout.
            // Only an explicit ITEM_ALREADY_OWNED response can establish that outcome.
            return null
        }
        remember(token)
        // An old callback received after a retry must not adopt the retry's context.
        val matching = attempt?.takeIf { purchaseTime >= it.createdAt }
        if (matching != null) tokens[token] = matching
        return matching
    }

    @Synchronized
    fun remember(token: String) {
        if (token.isNotBlank()) knownTokens.add(token)
        while (knownTokens.size > 512) knownTokens.remove(knownTokens.first())
    }

    @Synchronized
    fun pending(attempt: Attempt) {
        if (active === attempt) active = null
        emit(attempt, CheckoutStatus.PENDING, CheckoutStage.PURCHASE)
    }

    @Synchronized
    fun verified(attempt: Attempt) {
        emit(attempt, CheckoutStatus.VERIFIED, CheckoutStage.VERIFICATION)
    }

    @Synchronized
    fun invalid(attempt: Attempt, reason: CheckoutReason) {
        finish(attempt, CheckoutStatus.FAILED, CheckoutStage.VERIFICATION, reason = reason)
    }

    @Synchronized
    fun delivered(token: String, stage: CheckoutStage) {
        tokens[token]?.let { finish(it, CheckoutStatus.COMPLETED, stage) }
    }

    @Synchronized
    fun processingFailed(token: String, stage: CheckoutStage, code: Int) {
        tokens[token]?.let {
            // Payment can be valid even though acknowledgement/consumption needs retry.
            if (active === it) active = null
            emit(it, CheckoutStatus.PROCESSING_FAILED, stage, code, CheckoutReason.BILLING_RESPONSE)
        }
    }

    private fun responseFailure(attempt: Attempt, code: Int, stage: CheckoutStage) {
        val status = when (code) {
            1 -> CheckoutStatus.CANCELLED // BillingResponseCode.USER_CANCELED
            7 -> CheckoutStatus.ALREADY_OWNED // BillingResponseCode.ITEM_ALREADY_OWNED
            else -> CheckoutStatus.FAILED
        }
        finish(attempt, status, stage, code, CheckoutReason.BILLING_RESPONSE)
    }

    private fun finish(attempt: Attempt, status: CheckoutStatus, stage: CheckoutStage,
                       code: Int? = null, reason: CheckoutReason? = null) {
        if (attempt.terminal) return
        attempt.terminal = true
        if (active === attempt) active = null
        tokens.entries.removeAll { it.value === attempt }
        notify(attempt, status, stage, code, reason)
    }

    private fun emit(attempt: Attempt, status: CheckoutStatus, stage: CheckoutStage,
                     code: Int? = null, reason: CheckoutReason? = null) {
        if (attempt.terminal || !attempt.emitted.add(Triple(status, stage, code))) return
        notify(attempt, status, stage, code, reason)
    }

    private fun notify(attempt: Attempt, status: CheckoutStatus, stage: CheckoutStage,
                       code: Int?, reason: CheckoutReason?) {
        val event = CheckoutEvent(attempt.id, attempt.productId, attempt.period, status, stage, code, reason)
        // A diagnostics subscriber cannot break verification or entitlement delivery.
        runCatching { dispatch(attempt.listener, event) }
    }

    private fun expire() {
        active?.takeIf { now() - it.createdAt > 5 * 60 * 1000L }?.let {
            unresolved(it, CheckoutReason.TRACKING_EXPIRED)
        }
        val attempts = (tokens.values + listOfNotNull(active)).distinct()
        attempts.filter { now() - it.createdAt > 7 * 24 * 60 * 60 * 1000L }
            .forEach { unresolved(it, CheckoutReason.TRACKING_EXPIRED) }
        // Bound retained pending/failed-delivery callbacks even in unusually busy clients.
        if (attempts.size >= 32) attempts.minByOrNull { it.createdAt }?.let {
            unresolved(it, CheckoutReason.TRACKING_EXPIRED)
        }
    }
}
