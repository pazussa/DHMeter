package com.dhmeter.sensing

import com.dhmeter.domain.model.RawCaptureHandle
import kotlinx.coroutines.flow.StateFlow

/**
 * State of the recording session.
 */
sealed class RecordingState {
    data object Idle : RecordingState()
    
    data class Recording(
        val gpsAccuracy: Float,
        val movementDetected: Boolean, // Movement detected (speed > threshold)
        val signalStability: Float, // 0-1, where 1 is stable
        val currentSpeed: Float, // m/s
        // Live metrics (0-1 normalized)
        val liveImpact: Float = 0f,     // Current impact level
        val liveHarshness: Float = 0f,  // Current vibration level
        val liveStability: Float = 0f   // Current stability (higher = less stable)
    ) : RecordingState()
    
    data object Processing : RecordingState()
    
    data class Completed(val runId: String) : RecordingState()
    
    data class Error(val message: String) : RecordingState()
}

/**
 * Manages sensor recording sessions.
 */
interface RecordingManager {
    val recordingState: StateFlow<RecordingState>
    
    /**
     * Start recording sensor data.
     */
    suspend fun startRecording(trackId: String, placement: String): Result<Unit>
    
    /**
     * Stop recording and return handle to raw data.
     */
    suspend fun stopRecording(): RawCaptureHandle?
    
    /**
     * Cancel recording without saving.
     */
    fun cancelRecording()
}
