package com.dhmeter.app.data

import android.content.Context
import android.content.SharedPreferences
import com.dhmeter.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple preferences manager for app-wide settings.
 * Implements PreferencesRepository for domain layer access.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) : PreferencesRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    private val _includeInvalidRuns = MutableStateFlow(
        prefs.getBoolean(KEY_INCLUDE_INVALID_RUNS, false)
    )
    override val includeInvalidRuns: StateFlow<Boolean> = _includeInvalidRuns.asStateFlow()
    
    override fun getIncludeInvalidRuns(): Boolean = _includeInvalidRuns.value
    
    fun setIncludeInvalidRuns(value: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_INVALID_RUNS, value).apply()
        _includeInvalidRuns.value = value
    }
    
    companion object {
        private const val PREFS_NAME = "dhmeter_prefs"
        private const val KEY_INCLUDE_INVALID_RUNS = "include_invalid_runs"
    }
}
