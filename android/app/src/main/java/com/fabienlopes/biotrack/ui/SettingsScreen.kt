package com.fabienlopes.biotrack.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.annotation.StringRes
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.BioTrackViewModel
import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.HealthConnectionStatus
import com.fabienlopes.biotrack.data.ReminderTargetKind
import com.fabienlopes.biotrack.data.RoutineProfileKind
import com.fabienlopes.biotrack.domain.Planner
import com.fabienlopes.biotrack.integration.HealthConnectManager
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: BioTrackViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkMode by viewModel.darkMode.collectAsState()
    val showRecommendations by viewModel.showRecommendations.collectAsState()
    val healthStatus by viewModel.healthStatus.collectAsState()
    val syncing by viewModel.healthSyncing.collectAsState()
    val healthSyncDays by viewModel.healthSyncDays.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val selectedCheckInMetricIds by viewModel.selectedCheckInMetricIds.collectAsState()
    val healthManager = remember(context) { HealthConnectManager(context) }
    val unreadableImportMessage = stringResource(R.string.import_file_unreadable)
    val tooLargeMessage = stringResource(R.string.file_too_large)
    val healthSettingsUnavailableMessage = stringResource(R.string.health_settings_unavailable)
    val healthDisconnectFailedMessage = stringResource(R.string.health_disconnect_failed)
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    var encryptedExportDialog by remember { mutableStateOf(false) }
    var encryptedImportDialog by remember { mutableStateOf(false) }
    var encryptedImportRaw by remember { mutableStateOf<String?>(null) }
    var showResetOnboarding by remember { mutableStateOf(false) }
    var pendingImportedSnapshot by remember { mutableStateOf<AppSnapshot?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var showHealthDisconnect by remember { mutableStateOf(false) }
    var healthError by remember { mutableStateOf<String?>(null) }

    var pendingExportText by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val payload = pendingExportText
        if (uri != null && payload != null) context.contentResolver.openOutputStream(uri)?.writer()?.use { it.write(payload) }
        pendingExportText = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { readLimitedText(it, tooLargeMessage) }?.let(viewModel::decodeImport)
            }.onSuccess { imported -> pendingImportedSnapshot = imported }
                .onFailure { error -> importError = error.message ?: unreadableImportMessage }
        }
    }
    val encryptedImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input -> readLimitedText(input, tooLargeMessage) }
                    ?: error(unreadableImportMessage)
            }.onSuccess { raw ->
                encryptedImportRaw = raw
                encryptedImportDialog = true
            }.onFailure { error -> importError = error.message ?: unreadableImportMessage }
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        viewModel.setHealthStatus(if (granted.containsAll(healthManager.permissions)) HealthConnectionStatus.CONNECTED else HealthConnectionStatus.DENIED)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } })
    }) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsSection(stringResource(R.string.settings_general)) {
                SettingsToggleRow(Icons.Default.DarkMode, stringResource(R.string.dark_mode), darkMode) { viewModel.setDarkMode(it) }
                SettingsToggleRow(Icons.Default.Info, stringResource(R.string.show_recommendations_card), showRecommendations) { viewModel.setShowRecommendations(it) }
            }

            SettingsSection(stringResource(R.string.checkin_fields)) {
                val moveUpDescription = stringResource(R.string.move_up)
                val moveDownDescription = stringResource(R.string.move_down)
                val metricsById = snapshot.metrics.associateBy { it.id }
                val selectedMetrics = selectedCheckInMetricIds.mapNotNull { metricsById[it] }
                if (selectedMetrics.isEmpty()) {
                    Text(stringResource(R.string.no_tracking_metric_selected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                selectedMetrics.forEachIndexed { index, metric ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(metric.name)
                            Text(metric.unit ?: stringResource(if (metric.kind.name == "HOURS_MINUTES") R.string.duration_value else R.string.numeric_value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { viewModel.moveCheckInMetric(metric.id, -1) }, enabled = index > 0, modifier = Modifier.semantics { contentDescription = moveUpDescription }) { Text(stringResource(R.string.move_up_symbol)) }
                        TextButton(onClick = { viewModel.moveCheckInMetric(metric.id, 1) }, enabled = index < selectedMetrics.lastIndex, modifier = Modifier.semantics { contentDescription = moveDownDescription }) { Text(stringResource(R.string.move_down_symbol)) }
                        TextButton(onClick = { viewModel.setCheckInMetricSelected(metric.id, false) }) { Text(stringResource(R.string.remove)) }
                    }
                }
                val available = snapshot.metrics.filter { it.id !in selectedCheckInMetricIds }
                if (available.isNotEmpty()) Text(stringResource(R.string.add_from_tracking), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                available.forEach { metric ->
                    TextButton(onClick = { viewModel.setCheckInMetricSelected(metric.id, true) }) { Text(stringResource(R.string.add_named_item, metric.name)) }
                }
                TextButton(onClick = { viewModel.resetCheckInMetricSelection() }) { Text(stringResource(R.string.restore_default_selection)) }
            }

            SettingsSection(stringResource(R.string.settings_notifications)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.local_reminders), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.local_reminders_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.allow)) }
                }
            }

            SettingsSection(stringResource(R.string.settings_health)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.health_connect_name), fontWeight = FontWeight.SemiBold)
                        Text(healthStatusLabel(healthStatus, healthManager), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.health_connect_optional_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.sync_period), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 90, 365).forEach { days ->
                        FilterChip(
                            selected = healthSyncDays == days,
                            onClick = { viewModel.setHealthSyncDays(days) },
                            label = { Text(if (days == 365) stringResource(R.string.one_year) else stringResource(R.string.days_short, days)) }
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (healthStatus == HealthConnectionStatus.CONNECTED) {
                                runCatching { context.startActivity(healthManager.permissionsSettingsIntent()) }
                                    .onFailure { healthError = it.message ?: healthSettingsUnavailableMessage }
                            } else if (healthManager.availability() == HealthConnectManager.Availability.AVAILABLE) {
                                healthLauncher.launch(healthManager.permissions)
                            }
                        },
                        enabled = healthManager.availability() == HealthConnectManager.Availability.AVAILABLE,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(if (healthStatus == HealthConnectionStatus.CONNECTED) R.string.manage_access else R.string.connect_health_connect)) }
                    OutlinedButton(onClick = {
                        scope.launch {
                            viewModel.setHealthSyncing(true)
                            val granted = runCatching { healthManager.hasAllPermissions() }.getOrDefault(false)
                            if (granted) {
                                viewModel.syncHealthValues(healthManager.readDailyValues(healthSyncDays))
                                viewModel.setHealthStatus(HealthConnectionStatus.CONNECTED)
                            } else viewModel.setHealthStatus(HealthConnectionStatus.DENIED)
                            viewModel.setHealthSyncing(false)
                        }
                    }, enabled = !syncing && healthStatus == HealthConnectionStatus.CONNECTED, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(if (syncing) R.string.syncing else R.string.sync))
                    }
                    OutlinedButton(
                        onClick = { showHealthDisconnect = true },
                        enabled = !syncing && healthStatus == HealthConnectionStatus.CONNECTED,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.disconnect_health_connect)) }
                }
                if (healthManager.availability() == HealthConnectManager.Availability.PROVIDER_UPDATE_REQUIRED) {
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=com.google.android.apps.healthdata".toUri())) }) { Text(stringResource(R.string.install_health_connect)) }
                }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) { Text(stringResource(R.string.open_android_settings)) }
            }

            SettingsSection(stringResource(R.string.active_routine)) {
                val activeKind = Planner.activeKind(snapshot)
                val activeProfile = snapshot.routineProfiles.firstOrNull { it.kind == activeKind }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutineProfileKind.entries.forEach { kind ->
                        FilterChip(selected = activeKind == kind, onClick = { viewModel.setRoutineProfile(kind) }, label = { Text(kindDisplayName(kind)) })
                    }
                }
                Text(stringResource(R.string.protocols_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (snapshot.protocols.isEmpty()) Text(stringResource(R.string.no_protocol_created), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                snapshot.protocols.forEach { protocol ->
                    ProfileToggleRow(
                        title = protocol.name,
                        checked = activeProfile?.disabledProtocolIds?.contains(protocol.id) != true,
                        onChecked = { enabled -> viewModel.setProfileItemEnabled(activeKind, ReminderTargetKind.PROTOCOL, protocol.id, enabled) }
                    )
                }
                Text(stringResource(R.string.supplements_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (snapshot.supplements.isEmpty()) Text(stringResource(R.string.no_supplement_created), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                snapshot.supplements.forEach { supplement ->
                    ProfileToggleRow(
                        title = supplement.name,
                        checked = activeProfile?.disabledSupplementIds?.contains(supplement.id) != true,
                        onChecked = { enabled -> viewModel.setProfileItemEnabled(activeKind, ReminderTargetKind.SUPPLEMENT, supplement.id, enabled) }
                    )
                }
                Text(stringResource(R.string.reminders), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (snapshot.reminders.isEmpty()) Text(stringResource(R.string.no_reminder_created), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                snapshot.reminders.forEach { reminder ->
                    ProfileToggleRow(
                        title = reminder.title,
                        checked = activeProfile?.disabledReminderIds?.contains(reminder.id) != true,
                        onChecked = { enabled -> viewModel.setProfileReminderEnabled(activeKind, reminder.id, enabled) }
                    )
                }
                TextButton(onClick = { viewModel.resetRoutineProfile(activeKind) }) { Text(stringResource(R.string.reset_profile_choices)) }
            }

            SettingsSection(stringResource(R.string.backup)) {
                Text(stringResource(R.string.backup_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pendingExportText = viewModel.exportSnapshot(); exportLauncher.launch("ajuste-backup.json") }) { Icon(Icons.Default.Upload, contentDescription = null); Spacer(Modifier.size(4.dp)); Text(stringResource(R.string.export)) }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Icon(Icons.Default.Download, contentDescription = null); Spacer(Modifier.size(4.dp)); Text(stringResource(R.string.import_action)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { encryptedExportDialog = true }) { Icon(Icons.Default.Lock, contentDescription = null); Spacer(Modifier.size(4.dp)); Text(stringResource(R.string.encrypted_export)) }
                    OutlinedButton(onClick = { encryptedImportDialog = true }) { Icon(Icons.Default.Security, contentDescription = null); Spacer(Modifier.size(4.dp)); Text(stringResource(R.string.encrypted_import)) }
                }
            }

            SettingsSection(stringResource(R.string.privacy_information)) {
                Text(stringResource(R.string.non_medical_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { legalDocument = LegalDocument.PRIVACY }) { Text(stringResource(R.string.privacy_policy)) }
                TextButton(onClick = { legalDocument = LegalDocument.SUPPORT }) { Text(stringResource(R.string.support)) }
                TextButton(onClick = { legalDocument = LegalDocument.TERMS }) { Text(stringResource(R.string.terms_of_use)) }
                TextButton(onClick = { showResetOnboarding = true }) { Text(stringResource(R.string.review_onboarding)) }
                Text(stringResource(R.string.android_version), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (encryptedExportDialog) {
        PassphraseDialog(title = stringResource(R.string.encrypted_export), confirmTitle = stringResource(R.string.export), onDismiss = { encryptedExportDialog = false }) { passphrase ->
            pendingExportText = viewModel.exportEncrypted(passphrase.toCharArray())
            encryptedExportDialog = false
            exportLauncher.launch("ajuste-backup-secure.json")
        }
    }
    if (encryptedImportDialog) {
        if (encryptedImportRaw == null) {
            AlertDialog(onDismissRequest = { encryptedImportDialog = false }, title = { Text(stringResource(R.string.encrypted_import)) }, text = { Text(stringResource(R.string.choose_encrypted_file)) }, confirmButton = { Button(onClick = { encryptedImportDialog = false; encryptedImportLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text(stringResource(R.string.choose_file)) } }, dismissButton = { TextButton(onClick = { encryptedImportDialog = false }) { Text(stringResource(R.string.cancel)) } })
        } else {
            PassphraseDialog(title = stringResource(R.string.unlock_backup), confirmTitle = stringResource(R.string.import_action), onDismiss = { encryptedImportDialog = false; encryptedImportRaw = null }) { passphrase ->
                runCatching { viewModel.decodeEncryptedImport(encryptedImportRaw.orEmpty(), passphrase.toCharArray()) }
                    .onSuccess { imported ->
                        pendingImportedSnapshot = imported
                        encryptedImportDialog = false
                        encryptedImportRaw = null
                    }
                    .onFailure { error -> importError = error.message ?: unreadableImportMessage }
            }
        }
    }
    legalDocument?.let { document ->
        AlertDialog(onDismissRequest = { legalDocument = null }, title = { Text(stringResource(document.titleRes)) }, text = { Text(stringResource(document.bodyRes)) }, confirmButton = { TextButton(onClick = { legalDocument = null }) { Text(stringResource(R.string.close)) } })
    }
    if (showResetOnboarding) {
        AlertDialog(onDismissRequest = { showResetOnboarding = false }, title = { Text(stringResource(R.string.review_onboarding)) }, text = { Text(stringResource(R.string.review_onboarding_detail)) }, confirmButton = { Button(onClick = { showResetOnboarding = false; viewModel.reviewOnboarding() }) { Text(stringResource(R.string.understood)) } }, dismissButton = { TextButton(onClick = { showResetOnboarding = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (showHealthDisconnect) {
        AlertDialog(
            onDismissRequest = { showHealthDisconnect = false },
            title = { Text(stringResource(R.string.disconnect_health_connect)) },
            text = { Text(stringResource(R.string.disconnect_health_connect_confirmation)) },
            confirmButton = {
                Button(onClick = {
                    showHealthDisconnect = false
                    scope.launch {
                        runCatching { healthManager.revokeAllPermissions() }
                            .onSuccess { viewModel.setHealthStatus(HealthConnectionStatus.DENIED) }
                            .onFailure { healthError = it.message ?: healthDisconnectFailedMessage }
                    }
                }) { Text(stringResource(R.string.disconnect)) }
            },
            dismissButton = { TextButton(onClick = { showHealthDisconnect = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    pendingImportedSnapshot?.let { imported ->
        val protocolCount = pluralStringResource(R.plurals.protocol_count, imported.protocols.size, imported.protocols.size)
        val supplementCount = pluralStringResource(R.plurals.supplement_count, imported.supplements.size, imported.supplements.size)
        val metricCount = pluralStringResource(R.plurals.metric_count, imported.metrics.size, imported.metrics.size)
        val reminderCount = pluralStringResource(R.plurals.reminder_count, imported.reminders.size, imported.reminders.size)
        AlertDialog(
            onDismissRequest = { pendingImportedSnapshot = null },
            title = { Text(stringResource(R.string.confirm_import)) },
            text = { Text(stringResource(R.string.import_summary, protocolCount, supplementCount, metricCount, reminderCount)) },
            confirmButton = { Button(onClick = { viewModel.applyImportedSnapshot(imported); pendingImportedSnapshot = null }) { Text(stringResource(R.string.import_action)) } },
            dismissButton = { TextButton(onClick = { pendingImportedSnapshot = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.import_impossible)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text(stringResource(R.string.close)) } }
        )
    }
    healthError?.let { message ->
        AlertDialog(
            onDismissRequest = { healthError = null },
            title = { Text(stringResource(R.string.health_connect_name)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { healthError = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}

private fun readLimitedText(input: java.io.InputStream, tooLargeMessage: String, maxBytes: Int = 10 * 1024 * 1024): String {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { tooLargeMessage }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        BioCard { content() }
    }
}

@Composable
private fun SettingsToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(10.dp))
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ProfileToggleRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private enum class LegalDocument(@param:StringRes val titleRes: Int, @param:StringRes val bodyRes: Int) {
    PRIVACY(R.string.legal_privacy_title, R.string.legal_privacy_body),
    SUPPORT(R.string.support, R.string.legal_support_body),
    TERMS(R.string.terms_of_use, R.string.legal_terms_body)
}

@Composable
private fun healthStatusLabel(status: HealthConnectionStatus, manager: HealthConnectManager): String = stringResource(when {
    manager.availability() == HealthConnectManager.Availability.NOT_SUPPORTED -> R.string.health_not_available
    manager.availability() == HealthConnectManager.Availability.PROVIDER_UPDATE_REQUIRED -> R.string.health_update_required
    status == HealthConnectionStatus.CONNECTED -> R.string.health_connected
    status == HealthConnectionStatus.DENIED -> R.string.health_denied
    else -> R.string.health_not_connected
})

@Composable
private fun kindDisplayName(kind: RoutineProfileKind): String = stringResource(when (kind) {
    RoutineProfileKind.WEEKDAY -> R.string.profile_weekday
    RoutineProfileKind.WEEKEND -> R.string.profile_weekend
    RoutineProfileKind.TRAVEL -> R.string.profile_travel
})

@Composable
private fun PassphraseDialog(title: String, confirmTitle: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(passphrase, { passphrase = it }, label = { Text(stringResource(R.string.password)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true) }, confirmButton = { Button(onClick = { onConfirm(passphrase) }, enabled = passphrase.length >= 8) { Text(confirmTitle) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
