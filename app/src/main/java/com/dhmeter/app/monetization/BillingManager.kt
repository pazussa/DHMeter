package com.dropindh.app.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.PendingPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class BillingSubscriptionProduct(
    val productId: String,
    val title: String,
    val description: String,
    val priceText: String,
    val offerToken: String?,
    val hasFreeTrial: Boolean
)

data class BillingUiState(
    val isReady: Boolean = false,
    val isLoading: Boolean = false,
    val isPro: Boolean = false,
    val activeProductId: String? = null,
    val products: List<BillingSubscriptionProduct> = emptyList(),
    val errorCode: Int? = null,
    val errorMessage: String? = null
)

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val eventTracker: EventTracker,
    private val purchaseValidator: PurchaseValidator
) : PurchasesUpdatedListener {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val isConnecting = AtomicBoolean(false)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private val productDetailsById = mutableMapOf<String, ProductDetails>()

    private val _uiState = MutableStateFlow(
        BillingUiState(
            isPro = prefs.getBoolean(KEY_IS_PRO, false),
            activeProductId = prefs.getString(KEY_ACTIVE_PRODUCT_ID, null)
        )
    )
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    init {
        connectIfNeeded()
    }

    fun connectIfNeeded() {
        if (billingClient.isReady || isConnecting.get()) return
        if (!isConnecting.compareAndSet(false, true)) return
        _uiState.value = _uiState.value.copy(isLoading = true)

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                isConnecting.set(false)
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _uiState.value = _uiState.value.copy(
                        isReady = true,
                        isLoading = false,
                        errorCode = null,
                        errorMessage = null
                    )
                    queryProducts()
                    queryPurchases(reason = "initial_sync")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isReady = false,
                        isLoading = false,
                        errorCode = result.responseCode,
                        errorMessage = result.debugMessage
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting.set(false)
                _uiState.value = _uiState.value.copy(isReady = false)
            }
        })
    }

    fun refresh() {
        connectIfNeeded()
        if (billingClient.isReady) {
            queryProducts()
            queryPurchases(reason = "manual_refresh")
        }
    }

    fun restorePurchases() {
        connectIfNeeded()
        if (!billingClient.isReady) return
        queryPurchases(reason = "restore")
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorCode = null, errorMessage = null)
    }

    fun launchSubscriptionPurchase(activity: Activity, productId: String): Result<Unit> {
        connectIfNeeded()
        if (!billingClient.isReady) {
            return Result.failure(IllegalStateException("BILLING_NOT_READY"))
        }

        val productDetails = productDetailsById[productId]
            ?: return Result.failure(IllegalArgumentException("PRODUCT_NOT_LOADED"))

        val offerToken = productDetails.defaultOfferToken()
            ?: return Result.failure(IllegalStateException("OFFER_TOKEN_NOT_FOUND"))

        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params))
            .build()

        val response = billingClient.launchBillingFlow(activity, flowParams)
        if (response.responseCode != BillingClient.BillingResponseCode.OK) {
            _uiState.value = _uiState.value.copy(
                errorCode = response.responseCode,
                errorMessage = response.debugMessage
            )
            return Result.failure(
                IllegalStateException("LAUNCH_FAILED_${response.responseCode}")
            )
        }

        return Result.success(Unit)
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                handlePurchases(purchases.orEmpty(), reason = "purchase_update")
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _uiState.value = _uiState.value.copy(errorCode = null, errorMessage = null)
            }
            else -> {
                _uiState.value = _uiState.value.copy(
                    errorCode = billingResult.responseCode,
                    errorMessage = billingResult.debugMessage
                )
            }
        }
    }

    private fun queryProducts() {
        if (!billingClient.isReady) return
        val queryList = MonetizationCatalog.subscriptionProductIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(queryList)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _uiState.value = _uiState.value.copy(
                    errorCode = result.responseCode,
                    errorMessage = result.debugMessage
                )
                return@queryProductDetailsAsync
            }

            productDetailsById.clear()
            details.forEach { detail ->
                productDetailsById[detail.productId] = detail
            }

            val products = MonetizationCatalog.subscriptionProductIds.mapNotNull { productId ->
                val detail = productDetailsById[productId] ?: return@mapNotNull null
                val offer = detail.defaultOffer()
                val firstPhase = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()
                BillingSubscriptionProduct(
                    productId = detail.productId,
                    title = detail.title.substringBefore("(").trim().ifBlank { detail.title },
                    description = detail.description,
                    priceText = firstPhase?.formattedPrice ?: "--",
                    offerToken = offer?.offerToken,
                    hasFreeTrial = detail.hasFreeTrial()
                )
            }

            _uiState.value = _uiState.value.copy(
                products = products,
                errorCode = null,
                errorMessage = null
            )
        }
    }

    private fun queryPurchases(reason: String) {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _uiState.value = _uiState.value.copy(
                    errorCode = result.responseCode,
                    errorMessage = result.debugMessage
                )
                return@queryPurchasesAsync
            }
            handlePurchases(purchases, reason)
        }
    }

    private fun handlePurchases(
        purchases: List<Purchase>,
        reason: String
    ) {
        val activePurchases = purchases.filter { purchase ->
            purchaseValidator.validate(purchase) &&
                purchase.products.any { product ->
                    MonetizationCatalog.subscriptionProductIds.contains(product)
                }
        }

        activePurchases.forEach { purchase ->
            acknowledgeIfNeeded(purchase)
        }

        val activeProductId = activePurchases
            .flatMap { it.products }
            .firstOrNull { product -> MonetizationCatalog.subscriptionProductIds.contains(product) }

        val previousIsPro = _uiState.value.isPro
        val nextIsPro = activeProductId != null

        if (!previousIsPro && nextIsPro && reason == "purchase_update") {
            eventTracker.track(
                MonetizationEvents.PURCHASE_SUCCESS,
                mapOf("product_id" to activeProductId.orEmpty())
            )
            if (activeProductId != null && hasFreeTrial(activeProductId)) {
                eventTracker.track(
                    MonetizationEvents.TRIAL_START,
                    mapOf("product_id" to activeProductId)
                )
            }
        }

        if (previousIsPro && !nextIsPro) {
            eventTracker.track(MonetizationEvents.CHURN, mapOf("reason" to reason))
        }

        persistEntitlement(nextIsPro, activeProductId)
        _uiState.value = _uiState.value.copy(
            isPro = nextIsPro,
            activeProductId = activeProductId,
            errorCode = null,
            errorMessage = null
        )
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _uiState.value = _uiState.value.copy(
                    errorCode = result.responseCode,
                    errorMessage = result.debugMessage
                )
            }
        }
    }

    private fun hasFreeTrial(productId: String): Boolean {
        return productDetailsById[productId]?.hasFreeTrial() == true
    }

    private fun ProductDetails.defaultOffer():
        ProductDetails.SubscriptionOfferDetails? {
        val offers = subscriptionOfferDetails.orEmpty()
        if (offers.isEmpty()) return null
        return offers.minByOrNull { offer ->
            offer.pricingPhases.pricingPhaseList
                .firstOrNull()
                ?.priceAmountMicros
                ?: Long.MAX_VALUE
        }
    }

    private fun ProductDetails.defaultOfferToken(): String? {
        return defaultOffer()?.offerToken
    }

    private fun ProductDetails.hasFreeTrial(): Boolean {
        return subscriptionOfferDetails.orEmpty().any { offer ->
            offer.pricingPhases.pricingPhaseList.any { phase ->
                phase.priceAmountMicros == 0L
            }
        }
    }

    private fun persistEntitlement(isPro: Boolean, activeProductId: String?) {
        prefs.edit()
            .putBoolean(KEY_IS_PRO, isPro)
            .putString(KEY_ACTIVE_PRODUCT_ID, activeProductId)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "dropin_dh_billing"
        const val KEY_IS_PRO = "is_pro"
        const val KEY_ACTIVE_PRODUCT_ID = "active_product_id"
    }
}

