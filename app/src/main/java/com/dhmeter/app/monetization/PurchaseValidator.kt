package com.dropindh.app.monetization

import com.android.billingclient.api.Purchase
import javax.inject.Inject

interface PurchaseValidator {
    fun validate(purchase: Purchase): Boolean
}

class LocalPurchaseValidator @Inject constructor() : PurchaseValidator {
    override fun validate(purchase: Purchase): Boolean {
        // Backend verification hook:
        // replace this local check with server-side token validation before release.
        return purchase.purchaseToken.isNotBlank() &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
    }
}

