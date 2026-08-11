package com.fabienlopes.biotrack.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.BioTrackViewModel
import com.fabienlopes.biotrack.data.CheckInPeriod
import com.fabienlopes.biotrack.data.DailyCheckIn
import com.fabienlopes.biotrack.data.MetricKind
import com.fabienlopes.biotrack.data.Reminder
import com.fabienlopes.biotrack.data.RoutineProfileKind
import com.fabienlopes.biotrack.domain.Planner
import com.fabienlopes.biotrack.integration.HealthConnectManager
import com.fabienlopes.biotrack.notifications.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class AppTab(val label: String) {
    HOME("Checklist"),
    TRACK("Suivi"),
    STATS("Stats"),
    PROTOCOLS("Protocoles"),
    SUPPLEMENTS("Suppléments")
}

@Composable
fun BioTrackApp(viewModel: BioTrackViewModel) {
    val onboardingComplete by viewModel.onboardingComplete.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    if (!onboardingComplete) {
        OnboardingScreen(viewModel)
    } else if (settingsOpen) {
        SettingsScreen(viewModel = viewModel, onClose = { settingsOpen = false })
    } else {
        MainScaffold(
            viewModel = viewModel,
            snapshot = snapshot,
            onOpenSettings = { settingsOpen = true }
        )
    }
}

@Composable
private fun MainScaffold(
    viewModel: BioTrackViewModel,
    snapshot: AppSnapshot,
    onOpenSettings: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.lastMessage.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.HOME -> Icons.Default.Home
                                    AppTab.TRACK -> Icons.Default.Tune
                                    AppTab.STATS -> Icons.AutoMirrored.Filled.ShowChart
                                    AppTab.PROTOCOLS -> Icons.Default.Flag
                                    AppTab.SUPPLEMENTS -> Icons.Default.Favorite
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, maxLines = 1, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            when (selectedTab) {
                AppTab.HOME -> HomeScreen(viewModel, onOpenSettings, onNavigate = { selectedTab = it })
                AppTab.TRACK -> TrackScreen(viewModel)
                AppTab.STATS -> StatsScreen(viewModel)
                AppTab.PROTOCOLS -> ProtocolsScreen(viewModel)
                AppTab.SUPPLEMENTS -> SupplementsScreen(viewModel)
            }
        }
    }
}

@Composable
private fun OnboardingScreen(viewModel: BioTrackViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val healthManager = remember(context) { HealthConnectManager(context) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        viewModel.setHealthStatus(if (granted.containsAll(healthManager.permissions)) com.fabienlopes.biotrack.data.HealthConnectionStatus.CONNECTED else com.fabienlopes.biotrack.data.HealthConnectionStatus.DENIED)
        step = 2
    }
    val primary = MaterialTheme.colorScheme.primary

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AJUSTE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = primary, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text("${step + 1}/3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
            Spacer(Modifier.height(72.dp))
            Box(modifier = Modifier.size(92.dp).clip(CircleShape).background(primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (step) {
                        0 -> Icons.AutoMirrored.Filled.ShowChart
                        1 -> Icons.Default.Notifications
                        else -> Icons.Default.Favorite
                    },
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(46.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            AnimatedContent(targetState = step, label = "onboarding") { current ->
                Column {
                    Text(
                        when (current) {
                            0 -> "Votre suivi personnel, privé."
                            1 -> "Ne ratez pas vos routines."
                            else -> "Vos données santé, sous votre contrôle."
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when (current) {
                            0 -> "AJUSTE vous aide à observer vos routines, métriques, check-ins et protocoles sans compte ni serveur AJUSTE."
                            1 -> "Les notifications sont locales. Vous pouvez les activer maintenant ou plus tard dans les paramètres."
                            else -> stringResource(com.fabienlopes.biotrack.R.string.health_permissions_rationale)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(22.dp))
                    listOf(
                        "Données conservées sur l’appareil",
                        "Résultats exploratoires, sans diagnostic",
                        "Export JSON et sauvegarde chiffrée"
                    ).forEach { item ->
                        Row(modifier = Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(item)
                        }
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            when (step) {
                0 -> Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Continuer") }
                1 -> {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            step = 2
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Activer les notifications") }
                    TextButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Plus tard") }
                }
                else -> {
                    Button(
                        onClick = {
                            if (healthManager.availability() == HealthConnectManager.Availability.AVAILABLE) healthLauncher.launch(healthManager.permissions)
                            else step = 2
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (healthManager.availability() == HealthConnectManager.Availability.AVAILABLE) "Connecter Health Connect" else "Continuer sans Health Connect") }
                    TextButton(onClick = { viewModel.completeOnboarding() }, modifier = Modifier.fillMaxWidth()) { Text("Terminer plus tard") }
                }
            }
            if (step == 2) {
                Spacer(Modifier.height(8.dp))
                Text("Health Connect est disponible sur Android 9+ avec Google Play services. Vous pourrez modifier cet accès dans les paramètres.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { viewModel.completeOnboarding() }, modifier = Modifier.fillMaxWidth()) { Text("Commencer") }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    viewModel: BioTrackViewModel,
    onOpenSettings: () -> Unit,
    onNavigate: (AppTab) -> Unit
) {
    val context = LocalContext.current
    val snapshot by viewModel.snapshot.collectAsState()
    val showRecommendations by viewModel.showRecommendations.collectAsState()
    var checkInPeriod by remember { mutableStateOf<CheckInPeriod?>(null) }
    var reminderDialog by remember { mutableStateOf(false) }
    var recommendationsExpanded by rememberSaveable { mutableStateOf(true) }
    val plan = Planner.plan(snapshot)
    val protocols = Planner.protocolsScheduledToday(snapshot)
    val supplements = Planner.supplementsScheduledToday(snapshot)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Votre Checklist quotidienne", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(formatDate(System.currentTimeMillis()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Text("${plan.total - plan.done}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Paramètres") }
            }
        }
        if (showRecommendations) {
            item {
                BioCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recommandations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.refreshInsights() }) { Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir") }
                        IconButton(onClick = { recommendationsExpanded = !recommendationsExpanded }) { Icon(if (recommendationsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Déplier") }
                    }
                    if (recommendationsExpanded) {
                        if (snapshot.recommendations.isEmpty()) Text("Aucune recommandation pour le moment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        snapshot.recommendations.take(3).forEach { item ->
                            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                Text(item.title, fontWeight = FontWeight.SemiBold)
                                Text(item.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Text("${snapshot.recommendations.size} recommandation(s) disponible(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            BioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Check-ins quotidiens", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${listOf(CheckInPeriod.MORNING, CheckInPeriod.EVENING).count { period -> snapshot.dailyCheckIns.any { it.period == period && Planner.sameDay(it.date, System.currentTimeMillis()) } }}/2", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CheckInButton(CheckInPeriod.MORNING, snapshot, Modifier.weight(1f)) { checkInPeriod = CheckInPeriod.MORNING }
                    CheckInButton(CheckInPeriod.EVENING, snapshot, Modifier.weight(1f)) { checkInPeriod = CheckInPeriod.EVENING }
                }
            }
        }
        item {
            BioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Objectifs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${plan.done}/${plan.total}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { if (plan.total == 0) 0f else plan.done.toFloat() / plan.total }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape))
                Spacer(Modifier.height(6.dp))
                Text("${if (plan.total == 0) 0 else plan.done * 100 / plan.total}% réalisé aujourd'hui", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            BioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rappels pour aujourd'hui", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { reminderDialog = true }) { Text("Ajouter") }
                }
                if (snapshot.reminders.isEmpty()) {
                    Text("Aucun rappel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    snapshot.reminders.take(3).forEach { reminder ->
                        ReminderRow(reminder, viewModel)
                    }
                }
            }
        }
        item {
            HomeListCard(
                title = "Protocoles à suivre aujourd'hui",
                items = protocols.map { it.id to (it.name to (it.detail ?: Planner.frequencyLabel(it.frequency))) },
                emptyText = "Aucun protocole planifié.",
                onSeeAll = { onNavigate(AppTab.PROTOCOLS) },
                checked = { id -> Planner.isProtocolDoneToday(id, snapshot) },
                onToggle = { id -> viewModel.toggleProtocol(id) }
            )
        }
        item {
            HomeListCard(
                title = "Suppléments à suivre aujourd'hui",
                items = supplements.map { it.id to (it.name to (it.dose ?: it.timeContext ?: "À noter")) },
                emptyText = "Aucun supplément planifié.",
                onSeeAll = { onNavigate(AppTab.SUPPLEMENTS) },
                checked = { id -> Planner.isSupplementTakenToday(id, snapshot) },
                onToggle = { id -> viewModel.toggleSupplement(id) }
            )
        }
    }

    checkInPeriod?.let { period ->
        CheckInDialog(period, snapshot.dailyCheckIns.firstOrNull { it.period == period && Planner.sameDay(it.date, System.currentTimeMillis()) }, onDismiss = { checkInPeriod = null }) { energy, mood, sleep, stress, note ->
            viewModel.upsertCheckIn(period, energy, mood, sleepQuality = sleep, stress = stress, note = note)
            checkInPeriod = null
        }
    }
    if (reminderDialog) {
        ReminderDialog(onDismiss = { reminderDialog = false }) { reminder ->
            viewModel.addReminder(reminder)
            ReminderScheduler.schedule(context, reminder)
            reminderDialog = false
        }
    }
}

@Composable
private fun CheckInButton(period: CheckInPeriod, snapshot: AppSnapshot, modifier: Modifier, onClick: () -> Unit) {
    val done = snapshot.dailyCheckIns.any { it.period == period && Planner.sameDay(it.date, System.currentTimeMillis()) }
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = if (done) Color(0xFF2E9A60) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(period.displayName)
            Text(if (done) "Complété" else "À faire", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeListCard(
    title: String,
    items: List<Pair<String, Pair<String, String>>>,
    emptyText: String,
    onSeeAll: () -> Unit,
    checked: (String) -> Boolean,
    onToggle: (String) -> Unit
) {
    BioCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onSeeAll) { Text("Voir tous") }
        }
        if (items.isEmpty()) Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.take(3).forEach { (id, data) ->
            Row(modifier = Modifier.fillMaxWidth().clickable { onToggle(id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onToggle(id) }) { Icon(if (checked(id)) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = if (checked(id)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(data.first, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(data.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, viewModel: BioTrackViewModel) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Notifications, contentDescription = null, tint = if (reminder.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(reminder.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("%02d:%02d".format(reminder.hour, reminder.minute), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Switch(checked = reminder.enabled, onCheckedChange = { enabled ->
            viewModel.setReminderEnabled(reminder.id, enabled)
            val updated = reminder.copy(enabled = enabled)
            if (enabled) ReminderScheduler.schedule(context, updated) else ReminderScheduler.cancel(context, updated)
        })
    }
}

@Composable
private fun CheckInDialog(
    period: CheckInPeriod,
    existing: DailyCheckIn?,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int?, Int?, String?) -> Unit
) {
    var energy by remember { mutableFloatStateOf((existing?.energy ?: 6).toFloat()) }
    var mood by remember { mutableFloatStateOf((existing?.mood ?: 6).toFloat()) }
    val existingThird = if (period == CheckInPeriod.MORNING) existing?.sleepQuality else existing?.stress
    var third by remember { mutableFloatStateOf((existingThird ?: 5).toFloat()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check-in du ${period.displayName.lowercase(Locale.FRENCH)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RatingSlider("Énergie", energy) { energy = it }
                RatingSlider("Humeur", mood) { mood = it }
                RatingSlider(if (period == CheckInPeriod.MORNING) "Qualité du sommeil" else "Stress", third) { third = it }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (facultatif)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onSave(energy.toInt(), mood.toInt(), if (period == CheckInPeriod.MORNING) third.toInt() else null, if (period == CheckInPeriod.EVENING) third.toInt() else null, note) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun RatingSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value.toInt().toString(), fontWeight = FontWeight.Bold)
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = 1f..10f, steps = 8)
}

@Composable
private fun ReminderDialog(onDismiss: () -> Unit, onSave: (Reminder) -> Unit) {
    var title by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("08") }
    var minute by remember { mutableStateOf("00") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un rappel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Titre") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text("Heure") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text("Minute") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Note (facultatif)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(Reminder(title = title.ifBlank { "Rappel AJUSTE" }, hour = hour.toIntOrNull()?.coerceIn(0, 23) ?: 8, minute = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0, notes = notes.takeIf { it.isNotBlank() })) }, enabled = title.isNotBlank()) { Text("Ajouter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun BioCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

fun formatDate(timestamp: Long): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.FRENCH).format(Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()))

fun formatValue(value: Double, kind: MetricKind, unit: String?): String {
    return if (kind == MetricKind.HOURS_MINUTES) {
        val minutes = value.toInt()
        "%dh%02d".format(minutes / 60, minutes % 60)
    } else {
        "%.1f%s".format(Locale.FRENCH, value, if (unit.isNullOrBlank()) "" else " ${unit}")
    }
}
