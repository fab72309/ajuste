package com.fabienlopes.biotrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import com.fabienlopes.biotrack.data.BioTrackViewModel
import com.fabienlopes.biotrack.data.HealthConnectionStatus
import com.fabienlopes.biotrack.integration.HealthConnectManager
import com.fabienlopes.biotrack.notifications.ReminderScheduler
import com.fabienlopes.biotrack.ui.BioTrackApp
import com.fabienlopes.biotrack.ui.BioTrackTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: BioTrackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.rescheduleAll(this)
        setContent {
            BioTrackTheme(darkTheme = viewModel.darkMode.collectAsState().value) {
                BioTrackApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadIfExternallyUpdated()
        val healthManager = HealthConnectManager(this)
        if (healthManager.availability() != HealthConnectManager.Availability.AVAILABLE) {
            viewModel.setHealthStatus(HealthConnectionStatus.NOT_AVAILABLE)
            return
        }
        lifecycleScope.launch {
            val granted = runCatching { healthManager.hasAllPermissions() }.getOrDefault(false)
            if (!granted) {
                viewModel.setHealthStatus(HealthConnectionStatus.DENIED)
                return@launch
            }
            viewModel.setHealthStatus(HealthConnectionStatus.CONNECTED)
            viewModel.setHealthSyncing(true)
            runCatching { healthManager.readDailyValues(viewModel.healthSyncDays.value) }
                .onSuccess(viewModel::syncHealthValues)
            viewModel.setHealthSyncing(false)
        }
    }
}
