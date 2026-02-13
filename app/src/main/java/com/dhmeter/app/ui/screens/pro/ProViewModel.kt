package com.dropindh.app.ui.screens.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropindh.app.monetization.BillingManager
import com.dropindh.app.monetization.BillingSubscriptionProduct
import com.dropindh.app.monetization.EventTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

data class ProUiState(
    val isReady: Boolean = false,
    val isLoading: Boolean = false,
    val isPro: Boolean = false,
    val activeProductId: String? = null,
    val products: List<BillingSubscriptionProduct> = emptyList(),
    val errorCode: Int? = null,
    val errorMessage: String? = null,
    val actionErrorCode: String? = null
)

@HiltViewModel
class ProViewModel @Inject constructor(
    private val billingManager: BillingManager,
    private val eventTracker: EventTracker
) : ViewModel() {

    private val actionErrorCode = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProUiState> = combine(
        billingManager.uiState,
        actionErrorCode
    ) { billingState, actionError ->
        ProUiState(
            isReady = billingState.isReady,
            isLoading = billingState.isLoading,
            isPro = billingState.isPro,
            activeProductId = billingState.activeProductId,
            products = billingState.products,
            errorCode = billingState.errorCode,
            errorMessage = billingState.errorMessage,
            actionErrorCode = actionError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProUiState()
    )

    init {
        billingManager.refresh()
        eventTracker.trackPaywallView()
    }

    fun refresh() {
        actionErrorCode.value = null
        billingManager.clearError()
        billingManager.refresh()
    }

    fun restorePurchases() {
        actionErrorCode.value = null
        billingManager.clearError()
        billingManager.restorePurchases()
    }

    fun clearErrors() {
        actionErrorCode.value = null
        billingManager.clearError()
    }

    fun purchase(activity: Activity, productId: String) {
        actionErrorCode.value = null
        val result = billingManager.launchSubscriptionPurchase(activity, productId)
        result.onFailure { error ->
            actionErrorCode.update {
                error.message ?: "PURCHASE_LAUNCH_FAILED"
            }
        }
    }
}

