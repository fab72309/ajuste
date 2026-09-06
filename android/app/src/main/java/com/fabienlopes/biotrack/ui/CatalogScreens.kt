package com.fabienlopes.biotrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.BioTrackViewModel
import com.fabienlopes.biotrack.data.ProtocolItem
import com.fabienlopes.biotrack.data.Supplement
import com.fabienlopes.biotrack.domain.Planner
import kotlinx.coroutines.delay

@Composable
fun ProtocolsScreen(viewModel: BioTrackViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    var query by remember { mutableStateOf("") }
    var activeOnly by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf<String?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    var editorSeed by remember { mutableStateOf<ProtocolItem?>(null) }
    var editingExisting by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<ProtocolItem?>(null) }
    var logItem by remember { mutableStateOf<ProtocolItem?>(null) }
    var runningTimer by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableIntStateOf(0) }

    LaunchedEffect(runningTimer) {
        while (runningTimer != null && secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
            if (secondsLeft == 0) runningTimer = null
        }
    }

    val categories = snapshot.protocols.mapNotNull { it.category }.distinct().sorted()
    val filtered = snapshot.protocols.filter { item ->
        (!activeOnly || item.active) && (category == null || item.category == category) &&
            (query.isBlank() || item.name.contains(query, ignoreCase = true) ||
                item.detail.orEmpty().contains(query, ignoreCase = true) ||
                item.goal.orEmpty().contains(query, ignoreCase = true))
    }.sortedBy { it.name.lowercase() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCatalog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CatalogListHeader(
                    title = stringResource(R.string.protocols_title),
                    subtitle = stringResource(R.string.protocols_subtitle),
                    query = query,
                    onQueryChange = { query = it },
                    activeOnly = activeOnly,
                    onActiveOnlyChange = { activeOnly = it },
                    category = category,
                    categories = categories,
                    onCategoryChange = { category = it },
                    icon = { Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
                )
            }
            if (filtered.isEmpty()) {
                item { BioCard { Text(stringResource(R.string.no_protocol_results), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            items(filtered, key = { it.id }) { protocol ->
                ProtocolRow(
                    item = protocol,
                    isDone = Planner.isProtocolDoneToday(protocol.id, snapshot),
                    timerRunning = runningTimer == protocol.id,
                    secondsLeft = if (runningTimer == protocol.id) secondsLeft else 0,
                    onToggleDone = { viewModel.toggleProtocol(protocol.id) },
                    onToggleActive = { viewModel.toggleProtocolActive(protocol.id) },
                    onEdit = { editorSeed = protocol; editingExisting = true },
                    onDelete = { deleteCandidate = protocol },
                    onHistory = { logItem = protocol },
                    onTimer = {
                        if (runningTimer == protocol.id) {
                            runningTimer = null
                            secondsLeft = 0
                        } else {
                            runningTimer = protocol.id
                            secondsLeft = (protocol.targetMinutes ?: 10) * 60
                        }
                    }
                )
            }
        }
    }

    if (showCatalog) {
        ProtocolCatalogDialog(
            customTemplates = snapshot.customProtocolTemplates,
            onDismiss = { showCatalog = false },
            onBlank = { showCatalog = false; editingExisting = false; editorSeed = ProtocolItem(name = "") },
            onSelect = { showCatalog = false; editingExisting = false; editorSeed = it }
        )
    }
    editorSeed?.let { seed ->
        ProtocolEditorDialog(
            initial = seed,
            isEditing = editingExisting,
            onDismiss = { editorSeed = null },
            onSave = { item, saveTemplate ->
                if (editingExisting) viewModel.updateProtocol(item) else viewModel.addProtocol(item)
                if (saveTemplate) viewModel.saveProtocolTemplate(item)
                editorSeed = null
            }
        )
    }
    logItem?.let { item ->
        ProtocolLogsDialog(
            protocol = item,
            completions = snapshot.protocolCompletions.filter { it.protocolId == item.id },
            onDismiss = { logItem = null },
            onUpsert = viewModel::upsertProtocolCompletion,
            onDelete = viewModel::deleteProtocolCompletion
        )
    }
    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.delete_protocol_title)) },
            text = { Text(stringResource(R.string.delete_protocol_message, item.name)) },
            confirmButton = {
                Button(onClick = { viewModel.deleteProtocol(item.id); deleteCandidate = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ProtocolRow(
    item: ProtocolItem,
    isDone: Boolean,
    timerRunning: Boolean,
    secondsLeft: Int,
    onToggleDone: () -> Unit,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onHistory: () -> Unit,
    onTimer: () -> Unit
) {
    BioCard {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = isDone, onCheckedChange = { onToggleDone() })
            Column(modifier = Modifier.weight(1f).padding(top = 10.dp)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.detail ?: localizedFrequencyLabel(item.frequency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.category?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
            IconButton(onClick = onHistory) { Icon(Icons.Default.History, contentDescription = stringResource(R.string.open_history)) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (item.active) R.string.active else R.string.inactive),
                style = MaterialTheme.typography.labelMedium,
                color = if (item.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onTimer) {
                Icon(
                    if (timerRunning) Icons.Default.Stop else Icons.Default.Timer,
                    contentDescription = stringResource(if (timerRunning) R.string.stop_timer else R.string.start_timer),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (timerRunning) Text("%02d:%02d".format(secondsLeft / 60, secondsLeft % 60), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
            Switch(checked = item.active, onCheckedChange = { onToggleActive() })
        }
    }
}

@Composable
fun SupplementsScreen(viewModel: BioTrackViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    var query by remember { mutableStateOf("") }
    var activeOnly by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf<String?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    var editorSeed by remember { mutableStateOf<Supplement?>(null) }
    var editingExisting by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Supplement?>(null) }
    var logItem by remember { mutableStateOf<Supplement?>(null) }
    val categories = snapshot.supplements.mapNotNull { it.category }.distinct().sorted()
    val filtered = snapshot.supplements.filter { item ->
        (!activeOnly || item.active) && (category == null || item.category == category) &&
            (query.isBlank() || item.name.contains(query, ignoreCase = true) || item.brand.orEmpty().contains(query, ignoreCase = true) || item.dose.orEmpty().contains(query, ignoreCase = true))
    }.sortedBy { it.name.lowercase() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCatalog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CatalogListHeader(
                    title = stringResource(R.string.supplements_title),
                    subtitle = stringResource(R.string.supplements_subtitle),
                    query = query,
                    onQueryChange = { query = it },
                    activeOnly = activeOnly,
                    onActiveOnlyChange = { activeOnly = it },
                    category = category,
                    categories = categories,
                    onCategoryChange = { category = it },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
                )
            }
            if (filtered.isEmpty()) {
                item { BioCard { Text(stringResource(R.string.no_supplement_results), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            items(filtered, key = { it.id }) { supplement ->
                SupplementRow(
                    item = supplement,
                    isTaken = Planner.isSupplementTakenToday(supplement.id, snapshot),
                    onToggleTaken = { viewModel.toggleSupplement(supplement.id) },
                    onToggleActive = { viewModel.toggleSupplementActive(supplement.id) },
                    onEdit = { editorSeed = supplement; editingExisting = true },
                    onDelete = { deleteCandidate = supplement },
                    onHistory = { logItem = supplement }
                )
            }
        }
    }

    if (showCatalog) {
        SupplementCatalogDialog(
            customTemplates = snapshot.customSupplementTemplates,
            onDismiss = { showCatalog = false },
            onBlank = { showCatalog = false; editingExisting = false; editorSeed = Supplement(name = "") },
            onSelect = { showCatalog = false; editingExisting = false; editorSeed = it }
        )
    }
    editorSeed?.let { seed ->
        SupplementEditorDialog(
            initial = seed,
            isEditing = editingExisting,
            onDismiss = { editorSeed = null },
            onSave = { item, saveTemplate ->
                if (editingExisting) viewModel.updateSupplement(item) else viewModel.addSupplement(item)
                if (saveTemplate) viewModel.saveSupplementTemplate(item)
                editorSeed = null
            }
        )
    }
    logItem?.let { item ->
        SupplementLogsDialog(
            supplement = item,
            intakes = snapshot.supplementIntakes.filter { it.supplementId == item.id },
            onDismiss = { logItem = null },
            onUpsert = viewModel::upsertSupplementIntake,
            onDelete = viewModel::deleteSupplementIntake
        )
    }
    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.delete_supplement_title)) },
            text = { Text(stringResource(R.string.delete_supplement_message, item.name)) },
            confirmButton = {
                Button(onClick = { viewModel.deleteSupplement(item.id); deleteCandidate = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun SupplementRow(
    item: Supplement,
    isTaken: Boolean,
    onToggleTaken: () -> Unit,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onHistory: () -> Unit
) {
    BioCard {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = isTaken, onCheckedChange = { onToggleTaken() })
            Column(modifier = Modifier.weight(1f).padding(top = 10.dp)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val metadata = listOfNotNull(item.dose, item.brand, item.timeContext).joinToString(" · ")
                Text(metadata.ifBlank { localizedFrequencyLabel(item.frequency) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.category?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
            IconButton(onClick = onHistory) { Icon(Icons.Default.History, contentDescription = stringResource(R.string.open_history)) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (item.active) R.string.active else R.string.inactive),
                style = MaterialTheme.typography.labelMedium,
                color = if (item.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = item.active, onCheckedChange = { onToggleActive() })
        }
    }
}

@Composable
private fun CatalogListHeader(
    title: String,
    subtitle: String,
    query: String,
    onQueryChange: (String) -> Unit,
    activeOnly: Boolean,
    onActiveOnlyChange: (Boolean) -> Unit,
    category: String?,
    categories: List<String>,
    onCategoryChange: (String?) -> Unit,
    icon: @Composable () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            icon()
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text(stringResource(R.string.search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = activeOnly, onClick = { onActiveOnlyChange(!activeOnly) }, label = { Text(stringResource(R.string.active_filter)) })
            }
            item {
                FilterChip(selected = category == null, onClick = { onCategoryChange(null) }, label = { Text(stringResource(R.string.all_filter)) })
            }
            items(categories) { itemCategory ->
                FilterChip(selected = category == itemCategory, onClick = { onCategoryChange(itemCategory) }, label = { Text(itemCategory) })
            }
        }
    }
}
