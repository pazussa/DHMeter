package com.dropindh.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dropindh.app.service.RecordingService
import com.dropindh.app.ui.navigation.DHMeterNavHost
import com.dropindh.app.ui.theme.DHMeterTheme
import com.dropindh.app.ui.theme.DHRaceBackground
import com.dhmeter.domain.repository.TrackAutoStartRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var trackAutoStartRepository: TrackAutoStartRepository

    private val autoNavigateTrackId = MutableStateFlow<String?>(null)
    private val autoNavigateRunId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleAutoNavigateIntent(intent)
        startGlobalAutoMonitoringIfEnabled()

        setContent {
            val pendingAutoTrackId by autoNavigateTrackId.collectAsState()
            val pendingAutoRunId by autoNavigateRunId.collectAsState()
            DHMeterTheme {
                DHRaceBackground {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        DHMeterNavHost(
                            autoNavigateTrackId = pendingAutoTrackId,
                            autoNavigateRunId = pendingAutoRunId,
                            onAutoNavigateHandled = { handledTrackId ->
                                if (autoNavigateTrackId.value == handledTrackId) {
                                    autoNavigateTrackId.value = null
                                }
                            },
                            onAutoRunNavigateHandled = { handledRunId ->
                                if (autoNavigateRunId.value == handledRunId) {
                                    autoNavigateRunId.value = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutoNavigateIntent(intent)
    }

    private fun handleAutoNavigateIntent(intent: Intent?) {
        val trackId = intent?.getStringExtra(RecordingService.EXTRA_AUTO_NAVIGATE_TRACK_ID)
        if (!trackId.isNullOrBlank()) {
            autoNavigateTrackId.value = trackId
            intent.removeExtra(RecordingService.EXTRA_AUTO_NAVIGATE_TRACK_ID)
        }

        val runId = intent?.getStringExtra(RecordingService.EXTRA_AUTO_NAVIGATE_RUN_ID)
        if (!runId.isNullOrBlank()) {
            autoNavigateRunId.value = runId
            intent.removeExtra(RecordingService.EXTRA_AUTO_NAVIGATE_RUN_ID)
        }
    }

    private fun startGlobalAutoMonitoringIfEnabled() {
        val trackId = trackAutoStartRepository.getEnabledTrackIds().firstOrNull() ?: return
        runCatching {
            val intent = Intent(this, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START_FOREGROUND
                putExtra(RecordingService.EXTRA_TRACK_ID, trackId)
            }
            startForegroundService(intent)
        }
    }
}
