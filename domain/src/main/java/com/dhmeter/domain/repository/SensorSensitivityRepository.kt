package com.dhmeter.domain.repository

import com.dhmeter.domain.model.SensorSensitivitySettings
import kotlinx.coroutines.flow.StateFlow

interface SensorSensitivityRepository {
    val settings: StateFlow<SensorSensitivitySettings>
    val currentSettings: SensorSensitivitySettings

    suspend fun updateImpactSensitivity(value: Float)
    suspend fun updateHarshnessSensitivity(value: Float)
    suspend fun updateStabilitySensitivity(value: Float)
    suspend fun updateGpsSensitivity(value: Float)
    suspend fun resetToDefaults()
}

