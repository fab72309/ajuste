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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.annotation.StringRes
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.BioTrackViewModel
import com.fabienlopes.biotrack.data.CheckInPeriod
import com.fabienlopes.biotrack.data.DailyCheckIn
import com.fabienlopes.biotrack.data.Metric
import com.fabienlopes.biotrack.data.MetricKind
import com.fabienlopes.biotrack.data.Reminder
import com.fabienlopes.biotrack.data.RoutineProfileKind
import com.fabienlopes.biotrack.domain.Planner
import com.fabienlopes.biotrack.domain.PlannedItem
import com.fabienlopes.biotrack.domain.PlannedItemKind
import com.fabienlopes.biotrack.integration.HealthConnectManager
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class AppTab(@param:StringRes val labelRes: Int) {
    HOME(R.string.tab_home),
    TRACK(R.string.tab_track),
    STATS(R.string.tab_stats),
    PROTOCOLS(R.string.protocols_title),
    SUPPLEMENTS(R.string.supplements_title)
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
                    val tabLabel = stringResource(tab.labelRes)
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
                                contentDescription = tabLabel
                            )
                        },
                        label = { Text(tabLabel, maxLines = 1, fontSize = 10.sp) }
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
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = primary, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.onboarding_step, step + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            0 -> stringResource(R.string.onboarding_private_title)
                            1 -> stringResource(R.string.onboarding_routines_title)
                            else -> stringResource(R.string.onboarding_health_title)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when (current) {
                            0 -> stringResource(R.string.onboarding_private_body)
                            1 -> stringResource(R.string.onboarding_notifications_body)
                            else -> stringResource(R.string.health_permissions_rationale)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(22.dp))
                    listOf(
                        stringResource(R.string.onboarding_bullet_local),
                        stringResource(R.string.onboarding_bullet_exploratory),
                        stringResource(R.string.onboarding_bullet_export)
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
                0 -> Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.continue_action)) }
                1 -> {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            step = 2
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.enable_notifications)) }
                    TextButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.later)) }
                }
                else -> {
                    Button(
                        onClick = {
                            if (healthManager.availability() == HealthConnectManager.Availability.AVAILABLE) healthLauncher.launch(healthManager.permissions)
                            else step = 2
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(if (healthManager.availability() == HealthConnectManager.Availability.AVAILABLE) R.string.connect_health_connect else R.string.continue_without_health_connect)) }
                    TextButton(onClick = { viewModel.completeOnboarding() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.finish_later)) }
                }
            }
            if (step == 2) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.health_connect_onboarding_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { viewModel.completeOnboarding() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start)) }
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
    val snapshot by viewModel.snapshot.collectAsState()
    val selectedCheckInMetricIds by viewModel.selectedCheckInMetricIds.collectAsState()
    val showRecommendations by viewModel.showRecommendations.collectAsState()
    var checkInPeriod by remember { mutableStateOf<CheckInPeriod?>(null) }
    var reminderDialog by remember { mutableStateOf(false) }
    var reminderEditor by remember { mutableStateOf<Reminder?>(null) }
    var reminderDeleteCandidate by remember { mutableStateOf<Reminder?>(null) }
    var recommendationsExpanded by rememberSaveable { mutableStateOf(true) }
    val plan = Planner.plan(snapshot)
    val protocols = plan.items.filter { it.kind == PlannedItemKind.PROTOCOL }
    val supplements = plan.items.filter { it.kind == PlannedItemKind.SUPPLEMENT }
    val activeProfile = Planner.activeProfile(snapshot)
    val remindersToday = Planner.remindersScheduledToday(snapshot)
        .filter { activeProfile?.disabledReminderIds?.contains(it.id) != true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.daily_checklist), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(formatDate(System.currentTimeMillis()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Text("${plan.total - plan.done}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) }
            }
        }
        if (showRecommendations) {
            item {
                BioCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.recommendations), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.refreshInsights() }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh)) }
                        IconButton(onClick = { recommendationsExpanded = !recommendationsExpanded }) { Icon(if (recommendationsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = stringResource(if (recommendationsExpanded) R.string.collapse else R.string.expand)) }
                    }
                    if (recommendationsExpanded) {
                        if (snapshot.recommendations.isEmpty()) Text(stringResource(R.string.no_recommendations), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        snapshot.recommendations.take(3).forEach { item ->
                            val display = localizedRecommendation(item, snapshot)
                            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                Text(display.title, fontWeight = FontWeight.SemiBold)
                                Text(display.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Text(pluralStringResource(R.plurals.recommendations_available, snapshot.recommendations.size, snapshot.recommendations.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            BioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.daily_checkins), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.count_fraction, listOf(CheckInPeriod.MORNING, CheckInPeriod.EVENING).count { period -> snapshot.dailyCheckIns.any { it.period == period && Planner.sameDay(it.date, System.currentTimeMillis()) } }, 2), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(stringResource(R.string.goals), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.count_fraction, plan.done, plan.total), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { if (plan.total == 0) 0f else plan.done.toFloat() / plan.total }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.completion_today, if (plan.total == 0) 0 else plan.done * 100 / plan.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            BioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reminders_today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { reminderEditor = null; reminderDialog = true }) { Text(stringResource(R.string.add)) }
                }
                if (remindersToday.isEmpty()) {
                    Text(stringResource(R.string.no_reminder), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    remindersToday.forEach { reminder ->
                        ReminderRow(
                            reminder = reminder,
                            viewModel = viewModel,
                            onEdit = { reminderEditor = reminder; reminderDialog = true },
                            onDelete = { reminderDeleteCandidate = reminder }
                        )
                    }
                }
            }
        }
        item {
            HomeListCard(
                title = stringResource(R.string.protocols_today),
                items = protocols,
                emptyText = stringResource(R.string.no_protocol_scheduled),
                onSeeAll = { onNavigate(AppTab.PROTOCOLS) },
                onToggle = { item -> viewModel.toggleProtocolOccurrence(item.sourceId, item.occurrenceIndex) }
            )
        }
        item {
            HomeListCard(
                title = stringResource(R.string.supplements_today),
                items = supplements,
                emptyText = stringResource(R.string.no_supplement_scheduled),
                onSeeAll = { onNavigate(AppTab.SUPPLEMENTS) },
                onToggle = { item -> viewModel.toggleSupplementOccurrence(item.sourceId, item.occurrenceIndex) }
            )
        }
    }

    checkInPeriod?.let { period ->
        val selectedMetrics = selectedCheckInMetricIds.mapNotNull { id -> snapshot.metrics.firstOrNull { it.id == id } }
        val marker = "Check-in ${period.name}"
        val existingMetricValues = snapshot.metricEntries
            .filter { it.notes == marker && Planner.sameDay(it.date, System.currentTimeMillis()) }
            .associate { it.metricId to it.value }
        CheckInDialog(
            period = period,
            existing = snapshot.dailyCheckIns.firstOrNull { it.period == period && Planner.sameDay(it.date, System.currentTimeMillis()) },
            selectedMetrics = selectedMetrics,
            existingMetricValues = existingMetricValues,
            onDismiss = { checkInPeriod = null }
        ) { energy, mood, sleep, stress, note, metricValues ->
            viewModel.upsertCheckIn(period, energy, mood, sleepQuality = sleep, stress = stress, note = note, metricValues = metricValues)
            checkInPeriod = null
        }
    }
    if (reminderDialog) {
        ReminderDialog(existing = reminderEditor, onDismiss = { reminderDialog = false }) { reminder ->
            if (reminderEditor == null) viewModel.addReminder(reminder) else viewModel.updateReminder(reminder)
            reminderDialog = false
        }
    }
    reminderDeleteCandidate?.let { reminder ->
        AlertDialog(
            onDismissRequest = { reminderDeleteCandidate = null },
            title = { Text(stringResource(R.string.delete_reminder_title)) },
            text = { Text(stringResource(R.string.delete_reminder_message, reminder.title)) },
            confirmButton = { Button(onClick = { viewModel.deleteReminder(reminder.id); reminderDeleteCandidate = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { reminderDeleteCandidate = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun CheckInButton(period: CheckInPeriod, snapshot: AppSnapshot, modifier: Modifier, onClick: () -> Unit) {
    val done = snapshot.dailyCheckIns.any { it.period == period && Planner.sameDay(it.date, System.currentTimeMillis()) }
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = if (done) Color(0xFF2E9A60) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(checkInPeriodLabel(period))
            Text(stringResource(if (done) R.string.completed else R.string.to_do), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeListCard(
    title: String,
    items: List<PlannedItem>,
    emptyText: String,
    onSeeAll: () -> Unit,
    onToggle: (PlannedItem) -> Unit
) {
    BioCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.see_all)) }
        }
        if (items.isEmpty()) Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().clickable { onToggle(item) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onToggle(item) }) { Icon(if (item.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = if (item.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val frequency = localizedFrequencyLabel(item.frequency)
                    val occurrence = if (item.occurrenceCount > 1) {
                        if (item.kind == PlannedItemKind.SUPPLEMENT) {
                            stringResource(R.string.intake_occurrence, item.occurrenceIndex + 1, item.occurrenceCount)
                        } else {
                            stringResource(R.string.occurrence_with_frequency, item.occurrenceIndex + 1, item.occurrenceCount, frequency)
                        }
                    } else null
                    val subtitle = if (item.kind == PlannedItemKind.SUPPLEMENT) {
                        listOfNotNull(item.subtitle, occurrence).joinToString(" · ").ifBlank { frequency }
                    } else {
                        occurrence ?: frequency
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    viewModel: BioTrackViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Notifications, contentDescription = null, tint = if (reminder.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(reminder.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("%02d:%02d".format(reminder.hour, reminder.minute), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_reminder)) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_reminder)) }
        Switch(checked = reminder.enabled, onCheckedChange = { enabled ->
            viewModel.setReminderEnabled(reminder.id, enabled)
        })
    }
}

@Composable
private fun CheckInDialog(
    period: CheckInPeriod,
    existing: DailyCheckIn?,
    selectedMetrics: List<Metric>,
    existingMetricValues: Map<String, Double>,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int?, Int?, String?, Map<String, Double>) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    var energy by remember { mutableFloatStateOf((existing?.energy ?: 6).toFloat()) }
    var mood by remember { mutableFloatStateOf((existing?.mood ?: 6).toFloat()) }
    val existingThird = if (period == CheckInPeriod.MORNING) existing?.sleepQuality else existing?.stress
    var third by remember { mutableFloatStateOf((existingThird ?: 5).toFloat()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    val customMetrics = selectedMetrics.filter { defaultCheckInMetricKind(it) == null }
    var metricInputs by remember(period, selectedMetrics, existingMetricValues) {
        mutableStateOf(customMetrics.associate { metric ->
            metric.id to existingMetricValues[metric.id]?.let { value ->
                if (metric.kind == MetricKind.HOURS_MINUTES) "%d:%02d".format(value.toInt() / 60, value.toInt() % 60)
                else value.toString()
            }.orEmpty()
        })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checkin_title, checkInPeriodLabel(period).lowercase(locale))) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RatingSlider(stringResource(R.string.energy), energy) { energy = it }
                RatingSlider(stringResource(R.string.mood), mood) { mood = it }
                RatingSlider(stringResource(if (period == CheckInPeriod.MORNING) R.string.sleep_quality else R.string.stress), third) { third = it }
                customMetrics.forEach { metric ->
                    OutlinedTextField(
                        value = metricInputs[metric.id].orEmpty(),
                        onValueChange = { value -> metricInputs = metricInputs + (metric.id to value) },
                        label = { Text(metric.name) },
                        supportingText = { Text(if (metric.kind == MetricKind.HOURS_MINUTES) stringResource(R.string.hours_minutes_format) else metric.unit ?: stringResource(R.string.numeric_value)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.optional_note)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(onClick = {
            val values = mutableMapOf<String, Double>()
            selectedMetrics.forEach { metric ->
                when (defaultCheckInMetricKind(metric)) {
                    "energy" -> values[metric.id] = energy.toDouble()
                    "mood" -> values[metric.id] = mood.toDouble()
                    "sleep" -> if (period == CheckInPeriod.MORNING) values[metric.id] = third.toDouble()
                    "stress" -> if (period == CheckInPeriod.EVENING) values[metric.id] = third.toDouble()
                    else -> parseMetricInput(metricInputs[metric.id].orEmpty(), metric.kind)?.let { values[metric.id] = it }
                }
            }
            onSave(
                energy.toInt(),
                mood.toInt(),
                if (period == CheckInPeriod.MORNING) third.toInt() else null,
                if (period == CheckInPeriod.EVENING) third.toInt() else null,
                note,
                values
            )
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun checkInPeriodLabel(period: CheckInPeriod): String = stringResource(
    if (period == CheckInPeriod.MORNING) R.string.period_morning else R.string.period_evening
)

private fun defaultCheckInMetricKind(metric: Metric): String? {
    val name = java.text.Normalizer.normalize(metric.name, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(" ", "")
    return when {
        name.contains("energie") || name.contains("energy") -> "energy"
        name.contains("humeur") || name.contains("mood") -> "mood"
        name.contains("qualitedusommeil") || name.contains("sleepquality") -> "sleep"
        name.contains("stress") -> "stress"
        else -> null
    }
}

private fun parseMetricInput(raw: String, kind: MetricKind): Double? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (kind == MetricKind.HOURS_MINUTES && ':' in trimmed) {
        val parts = trimmed.split(':', limit = 2)
        val hours = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minutes = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return (hours.coerceAtLeast(0) * 60 + minutes.coerceIn(0, 59)).toDouble()
    }
    return trimmed.replace(',', '.').toDoubleOrNull()
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
private fun ReminderDialog(existing: Reminder?, onDismiss: () -> Unit, onSave: (Reminder) -> Unit) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var hour by remember(existing?.id) { mutableStateOf("%02d".format(existing?.hour ?: 8)) }
    var minute by remember(existing?.id) { mutableStateOf("%02d".format(existing?.minute ?: 0)) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var days by remember(existing?.id) { mutableStateOf(existing?.weekdays?.toSet().orEmpty()) }
    val labels = listOf(
        stringResource(R.string.weekday_initial_monday), stringResource(R.string.weekday_initial_tuesday),
        stringResource(R.string.weekday_initial_wednesday), stringResource(R.string.weekday_initial_thursday),
        stringResource(R.string.weekday_initial_friday), stringResource(R.string.weekday_initial_saturday),
        stringResource(R.string.weekday_initial_sunday)
    )
    val defaultReminderTitle = stringResource(R.string.default_reminder_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.add_reminder else R.string.edit_reminder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.reminder_title)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.hour_field)) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.minute_field)) }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.optional_note)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.days), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(stringResource(R.string.all_days_short) to (1..7).toSet(), stringResource(R.string.weekdays_short) to (1..5).toSet(), stringResource(R.string.weekend_short) to setOf(6, 7)).forEach { (label, preset) ->
                        FilterChip(selected = days == preset, onClick = { days = preset }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    labels.forEachIndexed { index, label ->
                        val day = index + 1
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = {
            val reminderId = existing?.id ?: java.util.UUID.randomUUID().toString()
            onSave(Reminder(
                id = reminderId,
                notificationBaseId = existing?.notificationBaseId ?: "reminder-$reminderId",
                title = title.ifBlank { defaultReminderTitle },
                hour = hour.toIntOrNull()?.coerceIn(0, 23) ?: 8,
                minute = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                weekdays = days.sorted(),
                notes = notes.takeIf { it.isNotBlank() },
                enabled = existing?.enabled ?: true,
                targetKind = existing?.targetKind,
                targetId = existing?.targetId
            ))
        }, enabled = title.isNotBlank()) { Text(stringResource(if (existing == null) R.string.add else R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
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

fun formatDate(timestamp: Long): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()))

fun formatValue(value: Double, kind: MetricKind, unit: String?): String {
    return if (kind == MetricKind.HOURS_MINUTES) {
        val minutes = value.toInt()
        "%dh%02d".format(minutes / 60, minutes % 60)
    } else {
        "%.1f%s".format(Locale.getDefault(), value, if (unit.isNullOrBlank()) "" else " ${unit}")
    }
}
