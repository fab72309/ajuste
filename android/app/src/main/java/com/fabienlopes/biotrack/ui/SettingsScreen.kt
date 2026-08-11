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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.fabienlopes.biotrack.data.BioTrackViewModel
import com.fabienlopes.biotrack.data.HealthConnectionStatus
import com.fabienlopes.biotrack.data.LocalStore
import com.fabienlopes.biotrack.data.RoutineProfileKind
import com.fabienlopes.biotrack.domain.Planner
import com.fabienlopes.biotrack.integration.HealthConnectManager
import com.fabienlopes.biotrack.notifications.ReminderScheduler
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
    val snapshot by viewModel.snapshot.collectAsState()
    val healthManager = remember(context) { HealthConnectManager(context) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    var encryptedExportDialog by remember { mutableStateOf(false) }
    var encryptedImportDialog by remember { mutableStateOf(false) }
    var encryptedImportRaw by remember { mutableStateOf<String?>(null) }
    var showResetOnboarding by remember { mutableStateOf(false) }

    var pendingExportText by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val payload = pendingExportText
        if (uri != null && payload != null) context.contentResolver.openOutputStream(uri)?.writer()?.use { it.write(payload) }
        pendingExportText = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }?.let { raw -> viewModel.applyImportedSnapshot(LocalStore(context).decode(raw)) }
            }.onFailure { viewModel.setHealthStatus(HealthConnectionStatus.NOT_CONNECTED) }
        }
    }
    val encryptedImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                encryptedImportRaw = reader.readText()
                encryptedImportDialog = true
            }
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        viewModel.setHealthStatus(if (granted.containsAll(healthManager.permissions)) HealthConnectionStatus.CONNECTED else HealthConnectionStatus.DENIED)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Paramètres") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } })
    }) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsSection("Général") {
                SettingsToggleRow(Icons.Default.DarkMode, "Mode sombre", darkMode) { viewModel.setDarkMode(it) }
                SettingsToggleRow(Icons.Default.Info, "Afficher la carte Recommandations", showRecommendations) { viewModel.setShowRecommendations(it) }
            }

            SettingsSection("Notifications") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rappels locaux", fontWeight = FontWeight.SemiBold)
                        Text("Les rappels ne quittent pas votre appareil.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Autoriser") }
                }
            }

            SettingsSection("Santé") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Health Connect", fontWeight = FontWeight.SemiBold)
                        Text(healthStatusLabel(healthStatus, healthManager), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Lecture facultative de sommeil, pas, poids, fréquence cardiaque au repos et HRV (RMSSD).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (healthManager.availability() == HealthConnectManager.Availability.AVAILABLE) healthLauncher.launch(healthManager.permissions) }, enabled = healthManager.availability() == HealthConnectManager.Availability.AVAILABLE) { Text("Gérer l'accès") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            viewModel.setHealthSyncing(true)
                            val granted = runCatching { healthManager.hasAllPermissions() }.getOrDefault(false)
                            if (granted) {
                                viewModel.syncHealthValues(healthManager.readDailyValues())
                                viewModel.setHealthStatus(HealthConnectionStatus.CONNECTED)
                            } else viewModel.setHealthStatus(HealthConnectionStatus.DENIED)
                            viewModel.setHealthSyncing(false)
                        }
                    }, enabled = !syncing && healthStatus == HealthConnectionStatus.CONNECTED) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text(if (syncing) "Synchronisation…" else "Synchroniser")
                    }
                }
                if (healthManager.availability() == HealthConnectManager.Availability.PROVIDER_UPDATE_REQUIRED) {
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=com.google.android.apps.healthdata".toUri())) }) { Text("Installer / mettre à jour Health Connect") }
                }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) { Text("Ouvrir les réglages Android") }
            }

            SettingsSection("Routine active") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutineProfileKind.entries.forEach { kind ->
                        FilterChip(selected = Planner.activeKind(snapshot) == kind, onClick = { viewModel.setRoutineProfile(kind) }, label = { Text(kindDisplayName(kind)) })
                    }
                }
            }

            SettingsSection("Sauvegarde") {
                Text("Les données restent dans le stockage privé de l'application. Les exports sont déclenchés par vous via le sélecteur Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pendingExportText = viewModel.exportSnapshot(); exportLauncher.launch("biotrack-backup.json") }) { Icon(Icons.Default.Upload, contentDescription = null); Spacer(Modifier.size(4.dp)); Text("Exporter") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Icon(Icons.Default.Download, contentDescription = null); Spacer(Modifier.size(4.dp)); Text("Importer") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { encryptedExportDialog = true }) { Icon(Icons.Default.Lock, contentDescription = null); Spacer(Modifier.size(4.dp)); Text("Exporter chiffré") }
                    OutlinedButton(onClick = { encryptedImportDialog = true }) { Icon(Icons.Default.Security, contentDescription = null); Spacer(Modifier.size(4.dp)); Text("Importer chiffré") }
                }
            }

            SettingsSection("Confidentialité et informations") {
                Text("AJUSTE est un outil d’auto-observation. Les statistiques sont exploratoires et ne constituent ni diagnostic ni conseil médical.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { legalDocument = LegalDocument.PRIVACY }) { Text("Politique de confidentialité") }
                TextButton(onClick = { legalDocument = LegalDocument.SUPPORT }) { Text("Support") }
                TextButton(onClick = { legalDocument = LegalDocument.TERMS }) { Text("Conditions d'utilisation") }
                TextButton(onClick = { showResetOnboarding = true }) { Text("Revoir l'accueil") }
                Text("Version Android 1.2.4 (9)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (encryptedExportDialog) {
        PassphraseDialog(title = "Exporter chiffré", confirmTitle = "Exporter", onDismiss = { encryptedExportDialog = false }) { passphrase ->
            pendingExportText = viewModel.exportEncrypted(passphrase.toCharArray())
            encryptedExportDialog = false
            exportLauncher.launch("biotrack-backup-secure.json")
        }
    }
    if (encryptedImportDialog) {
        if (encryptedImportRaw == null) {
            AlertDialog(onDismissRequest = { encryptedImportDialog = false }, title = { Text("Importer chiffré") }, text = { Text("Sélectionnez d'abord le fichier chiffré à importer.") }, confirmButton = { Button(onClick = { encryptedImportDialog = false; encryptedImportLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Choisir un fichier") } }, dismissButton = { TextButton(onClick = { encryptedImportDialog = false }) { Text("Annuler") } })
        } else {
            PassphraseDialog(title = "Déverrouiller la sauvegarde", confirmTitle = "Importer", onDismiss = { encryptedImportDialog = false; encryptedImportRaw = null }) { passphrase ->
                runCatching { viewModel.importEncrypted(encryptedImportRaw.orEmpty(), passphrase.toCharArray()) }
                encryptedImportDialog = false
                encryptedImportRaw = null
            }
        }
    }
    legalDocument?.let { document ->
        AlertDialog(onDismissRequest = { legalDocument = null }, title = { Text(document.title) }, text = { Text(document.body) }, confirmButton = { TextButton(onClick = { legalDocument = null }) { Text("Fermer") } })
    }
    if (showResetOnboarding) {
        AlertDialog(onDismissRequest = { showResetOnboarding = false }, title = { Text("Revoir l'accueil") }, text = { Text("Les données locales seront conservées. L'écran d'accueil sera présenté au prochain retour.") }, confirmButton = { Button(onClick = { showResetOnboarding = false; viewModel.reviewOnboarding() }) { Text("Compris") } }, dismissButton = { TextButton(onClick = { showResetOnboarding = false }) { Text("Annuler") } })
    }
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

private enum class LegalDocument(val title: String, val body: String) {
    PRIVACY("Confidentialité", "AJUSTE conserve les routines, métriques, check-ins, protocoles et exports dans le stockage privé de l'appareil. Health Connect est facultatif : seules les données autorisées sont lues pour préremplir localement des métriques. AJUSTE ne vend pas ces données et n'utilise pas de compte distant."),
    SUPPORT("Support", "Pour signaler un problème, utilisez la page support publiée avec l'application et joignez la version Android ainsi que les étapes permettant de reproduire le problème. N'envoyez pas de données de santé dans une demande de support."),
    TERMS("Conditions d'utilisation", "AJUSTE est un outil d'auto-observation personnelle. Les résultats statistiques sont exploratoires et ne remplacent pas l'avis d'un professionnel de santé. Vous restez responsable des routines et données que vous saisissez.")
}

private fun healthStatusLabel(status: HealthConnectionStatus, manager: HealthConnectManager): String = when {
    manager.availability() == HealthConnectManager.Availability.NOT_SUPPORTED -> "Health Connect n'est pas disponible sur cet appareil"
    manager.availability() == HealthConnectManager.Availability.PROVIDER_UPDATE_REQUIRED -> "Mise à jour Health Connect requise"
    status == HealthConnectionStatus.CONNECTED -> "Connecté et prêt à synchroniser"
    status == HealthConnectionStatus.DENIED -> "Accès non accordé"
    else -> "Non connecté"
}

private fun kindDisplayName(kind: RoutineProfileKind): String = when (kind) {
    RoutineProfileKind.WEEKDAY -> "Semaine"
    RoutineProfileKind.WEEKEND -> "Weekend"
    RoutineProfileKind.TRAVEL -> "Voyage"
}

@Composable
private fun PassphraseDialog(title: String, confirmTitle: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Mot de passe") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true) }, confirmButton = { Button(onClick = { onConfirm(passphrase) }, enabled = passphrase.length >= 8) { Text(confirmTitle) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}
