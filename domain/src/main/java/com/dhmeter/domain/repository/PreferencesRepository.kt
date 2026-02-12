package com.dhmeter.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for app preferences that need to be accessed from domain layer.
 */
interface PreferencesRepository {
    /**
     * Whether to include invalid runs in comparisons and charts.
     */
    val includeInvalidRuns: StateFlow<Boolean>
    
    /**
     * Get current value of includeInvalidRuns synchronously.
     */
    fun getIncludeInvalidRuns(): Boolean

    /**
     * Update includeInvalidRuns preference.
     */
    fun setIncludeInvalidRuns(value: Boolean)
}
