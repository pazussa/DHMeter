package com.dropindh.app.monetization

object MonetizationCatalog {
    const val PRODUCT_PRO_MONTHLY = "pro_monthly"
    const val PRODUCT_PRO_YEARLY = "pro_yearly"

    val subscriptionProductIds = listOf(
        PRODUCT_PRO_MONTHLY,
        PRODUCT_PRO_YEARLY
    )
}

object MonetizationEvents {
    const val INSTALL = "install"
    const val ONBOARDING_COMPLETE = "onboarding_complete"
    const val FIRST_RUN_COMPLETE = "first_run_complete"
    const val PAYWALL_VIEW = "paywall_view"
    const val TRIAL_START = "trial_start"
    const val PURCHASE_SUCCESS = "purchase_success"
    const val CHURN = "churn"

    const val COMMUNITY_REPORT_SUBMITTED = "community_report_submitted"
    const val COMMUNITY_USER_BLOCKED = "community_user_blocked"
    const val ACCOUNT_DELETION_STARTED = "account_deletion_started"
    const val ACCOUNT_DELETION_COMPLETED = "account_deletion_completed"
}

