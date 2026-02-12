package com.dhmeter.data.repository

import android.content.Context
import com.dhmeter.domain.model.SensorSensitivitySettings
import com.dhmeter.domain.repository.SensorSensitivityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorSensitivityRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : SensorSensitivityRepository {

    companion object {
        private const val PREFS_NAME = "dhmeter_sensor_sensitivity"
        private const val KEY_IMPACT = "impact_sensitivity"
        private const val KEY_HARSHNESS = "harshness_sensitivity"
        private const val KEY_STABILITY = "stability_sensitivity"
        private const val KEY_GPS = "gps_sensitivity"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(readSettings())

    override val settings: StateFlow<SensorSensitivitySettings> = mutableSettings.asStateFlow()
    override val currentSettings: SensorSensitivitySettings
        get() = mutableSettings.value

    override suspend fun updateImpactSensitivity(value: Float) {
        writeSettings(currentSettings.copy(impactSensitivity = value))
    }

    override suspend fun updateHarshnessSensitivity(value: Float) {
        writeSettings(currentSettings.copy(harshnessSensitivity = value))
    }

    override suspend fun updateStabilitySensitivity(value: Float) {
        writeSettings(currentSettings.copy(stabilitySensitivity = value))
    }

    override suspend fun updateGpsSensitivity(value: Float) {
        writeSettings(currentSettings.copy(gpsSensitivity = value))
    }

    override suspend fun resetToDefaults() {
        writeSettings(SensorSensitivitySettings())
    }

    private fun readSettings(): SensorSensitivitySettings {
        return SensorSensitivitySettings(
            impactSensitivity = prefs.getFloat(
                KEY_IMPACT,
                SensorSensitivitySettings.DEFAULT_IMPACT_SENSITIVITY
            ),
            harshnessSensitivity = prefs.getFloat(
                KEY_HARSHNESS,
                SensorSensitivitySettings.DEFAULT_HARSHNESS_SENSITIVITY
            ),
            stabilitySensitivity = prefs.getFloat(
                KEY_STABILITY,
                SensorSensitivitySettings.DEFAULT_STABILITY_SENSITIVITY
            ),
            gpsSensitivity = prefs.getFloat(
                KEY_GPS,
                SensorSensitivitySettings.DEFAULT_GPS_SENSITIVITY
            )
        ).normalized()
    }

    private fun writeSettings(settings: SensorSensitivitySettings) {
        val normalized = settings.normalized()
        prefs.edit()
            .putFloat(KEY_IMPACT, normalized.impactSensitivity)
            .putFloat(KEY_HARSHNESS, normalized.harshnessSensitivity)
            .putFloat(KEY_STABILITY, normalized.stabilitySensitivity)
            .putFloat(KEY_GPS, normalized.gpsSensitivity)
            .apply()
        mutableSettings.value = normalized
    }
}
