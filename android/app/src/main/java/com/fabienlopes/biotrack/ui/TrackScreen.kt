package com.fabienlopes.biotrack.ui

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.BioTrackViewModel
import com.fabienlopes.biotrack.data.Metric
import com.fabienlopes.biotrack.data.MetricEntry
import com.fabienlopes.biotrack.data.MetricKind
import com.fabienlopes.biotrack.domain.HistoryPeriod
import com.fabienlopes.biotrack.domain.TrackingAnalytics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun TrackScreen(viewModel: BioTrackViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    val context = LocalContext.current
    var selectedMetricId by remember(snapshot.metrics) { mutableStateOf(snapshot.metrics.firstOrNull()?.id) }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var showCreateMetric by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var addEntryFor by remember { mutableStateOf<String?>(null) }
    var editingEntry by remember { mutableStateOf<MetricEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<MetricEntry?>(null) }
    var historyMetricId by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf(emptySet<String>()) }
    var historyPeriod by remember { mutableStateOf(HistoryPeriod.ALL) }
    var entriesToShow by remember { mutableStateOf(10) }
    var csvToWrite by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val payload = csvToWrite
        if (uri != null && payload != null) context.contentResolver.openOutputStream(uri)?.writer()?.use { it.write(payload) }
        csvToWrite = null
    }
    val selectedMetric = snapshot.metrics.firstOrNull { it.id == selectedMetricId } ?: snapshot.metrics.firstOrNull()
    val filteredEntries = TrackingAnalytics.filterHistory(snapshot.metricEntries, snapshot.metrics, historyMetricId, categories, historyPeriod)

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tracking_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.tracking_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { showCreateMetric = true }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_metric)) }
            }
        }
        item {
            BioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.entry_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDate(selectedDate), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = { showDatePicker(context, selectedDate) { selectedDate = it } }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.choose))
                    }
                }
            }
        }
        if (snapshot.metrics.isEmpty()) {
            item {
                BioCard {
                    Text(stringResource(R.string.first_metric_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { showCreateMetric = true }) { Text(stringResource(R.string.create_metric)) }
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(snapshot.metrics, key = { it.id }) { metric ->
                        FilterChip(selected = metric.id == selectedMetric?.id, onClick = { selectedMetricId = metric.id }, label = { Text(metric.name, maxLines = 1) })
                    }
                }
            }
            selectedMetric?.let { metric ->
                item { MetricTrendCard(metric, snapshot) { addEntryFor = metric.id } }
                item {
                    BioCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.new_measurement), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.metric_date, metric.name, formatDate(selectedDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { addEntryFor = metric.id }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.add))
                            }
                        }
                    }
                }
            }
        }
        item {
            BioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(pluralStringResource(R.plurals.filtered_entries, filteredEntries.size, filteredEntries.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showFilters = true }) { Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.filter_history)) }
                    IconButton(onClick = {
                        csvToWrite = TrackingAnalytics.metricsCsv(snapshot.metrics, filteredEntries)
                        exportLauncher.launch("ajuste-suivi.csv")
                    }, enabled = filteredEntries.isNotEmpty()) { Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.export_history_csv)) }
                }
                if (filteredEntries.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.no_filtered_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    filteredEntries.take(entriesToShow).forEach { entry ->
                        val metric = snapshot.metrics.firstOrNull { it.id == entry.metricId }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(metric?.name ?: stringResource(R.string.deleted_metric), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.entry_date_source, formatDate(entry.date), sourceLabel(entry)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                entry.notes?.takeIf { isManualSource(entry) }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text(metric?.let { formatValue(entry.value, it.kind, it.unit) } ?: entry.value.toString(), fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { editingEntry = entry }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_entry)) }
                            IconButton(onClick = { deleteEntry = entry }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_entry)) }
                        }
                    }
                    if (entriesToShow < filteredEntries.size) {
                        OutlinedButton(onClick = { entriesToShow += 10 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.show_ten_more)) }
                    }
                }
            }
        }
        item {
            BioCard {
                Text(stringResource(R.string.recent_checkins), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                snapshot.dailyCheckIns.sortedByDescending { it.date }.take(4).forEach { checkIn ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.checkin_date_period, formatDate(checkIn.date), checkInPeriodLabel(checkIn.period)), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.checkin_scores, checkIn.energy, checkIn.mood), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (snapshot.dailyCheckIns.isEmpty()) Text(stringResource(R.string.checkins_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showCreateMetric) AddMetricDialog(onDismiss = { showCreateMetric = false }) { name, kind, unit ->
        viewModel.addMetric(name, kind, unit)
        showCreateMetric = false
    }
    addEntryFor?.let { metricId -> snapshot.metrics.firstOrNull { it.id == metricId }?.let { metric ->
        MetricEntryDialog(stringResource(R.string.add_metric_entry_title, metric.name), metric, null, { addEntryFor = null }) { value, notes ->
            viewModel.addMetricEntry(metric.id, value, notes, selectedDate)
            addEntryFor = null
            entriesToShow = 10
        }
    } }
    editingEntry?.let { entry -> snapshot.metrics.firstOrNull { it.id == entry.metricId }?.let { metric ->
        MetricEntryDialog(stringResource(R.string.edit_metric_entry_title, metric.name), metric, entry, { editingEntry = null }) { value, notes ->
            viewModel.updateMetricEntry(entry.copy(value = value, notes = notes))
            editingEntry = null
        }
    } }
    deleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteEntry = null },
            title = { Text(stringResource(R.string.delete_entry_title)) },
            text = { Text(stringResource(R.string.delete_entry_message, snapshot.metrics.firstOrNull { it.id == entry.metricId }?.name ?: stringResource(R.string.generic_metric), formatDate(entry.date))) },
            confirmButton = { TextButton(onClick = { viewModel.deleteMetricEntry(entry.id); deleteEntry = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteEntry = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (showFilters) HistoryFilterDialog(snapshot.metrics, historyMetricId, categories, historyPeriod, { showFilters = false }) { metricId, selectedCategories, selectedPeriod ->
        historyMetricId = metricId
        categories = selectedCategories
        historyPeriod = selectedPeriod
        entriesToShow = 10
        showFilters = false
    }
}

@Composable
private fun AddMetricDialog(onDismiss: () -> Unit, onSave: (String, MetricKind, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.create_metric)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(unit, { unit = it }, label = { Text(stringResource(R.string.unit_optional)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(duration, { duration = it }); Text(stringResource(R.string.duration_hours_minutes)) }
        }
    }, confirmButton = { Button(onClick = { onSave(name, if (duration) MetricKind.HOURS_MINUTES else MetricKind.NUMBER, unit.takeIf { it.isNotBlank() }) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.create)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

private fun showDatePicker(context: android.content.Context, timestamp: Long, onSelected: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    DatePickerDialog(context, { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day).atStartOfDay(zone).toInstant().toEpochMilli()) }, current.year, current.monthValue - 1, current.dayOfMonth).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

private fun isManualSource(entry: MetricEntry): Boolean {
    val notes = entry.notes.orEmpty().trim().lowercase(Locale.ROOT)
    return !isCheckInSource(notes) && notes != "hk" && "health" !in notes && !notes.startsWith("import:")
}

@Composable
private fun sourceLabel(entry: MetricEntry): String {
    val notes = entry.notes.orEmpty().trim().lowercase(Locale.ROOT)
    return stringResource(when {
        isCheckInSource(notes) -> R.string.source_checkin
        notes == "hk" || "health" in notes -> R.string.source_health
        notes.startsWith("import:") -> R.string.source_import
        else -> R.string.source_manual
    })
}

private fun isCheckInSource(normalizedNotes: String): Boolean =
    normalizedNotes.startsWith("check-in ") || normalizedNotes.startsWith("checkin:")
