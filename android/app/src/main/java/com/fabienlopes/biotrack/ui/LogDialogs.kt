package com.fabienlopes.biotrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.ProtocolCompletion
import com.fabienlopes.biotrack.data.ProtocolItem
import com.fabienlopes.biotrack.data.Supplement
import com.fabienlopes.biotrack.data.SupplementIntake
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val logTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ProtocolLogsDialog(
    protocol: ProtocolItem,
    completions: List<ProtocolCompletion>,
    onDismiss: () -> Unit,
    onUpsert: (ProtocolCompletion) -> Unit,
    onDelete: (String) -> Unit
) {
    var showEditor by remember(protocol.id) { mutableStateOf(false) }
    var editing by remember(protocol.id) { mutableStateOf<ProtocolCompletion?>(null) }
    if (showEditor) {
        ProtocolCompletionEditorDialog(
            protocol = protocol,
            existing = editing,
            onDismiss = { showEditor = false },
            onSave = { onUpsert(it); showEditor = false }
        )
        return
    }
    LogHistoryDialog(
        title = stringResource(R.string.protocol_history_title, protocol.name),
        empty = completions.isEmpty(),
        onDismiss = onDismiss,
        onAdd = { editing = null; showEditor = true }
    ) {
        items(completions.sortedByDescending { it.date }, key = { it.id }) { completion ->
            LogRow(
                date = completion.date,
                status = stringResource(if (completion.completed) R.string.completed_status else R.string.not_completed_status),
                detail = listOfNotNull(completion.goal, completion.intervention, completion.notes).firstOrNull().orEmpty(),
                onEdit = { editing = completion; showEditor = true },
                onDelete = { onDelete(completion.id) }
            )
        }
    }
}

@Composable
fun SupplementLogsDialog(
    supplement: Supplement,
    intakes: List<SupplementIntake>,
    onDismiss: () -> Unit,
    onUpsert: (SupplementIntake) -> Unit,
    onDelete: (String) -> Unit
) {
    var showEditor by remember(supplement.id) { mutableStateOf(false) }
    var editing by remember(supplement.id) { mutableStateOf<SupplementIntake?>(null) }
    if (showEditor) {
        SupplementIntakeEditorDialog(
            supplement = supplement,
            existing = editing,
            onDismiss = { showEditor = false },
            onSave = { onUpsert(it); showEditor = false }
        )
        return
    }
    LogHistoryDialog(
        title = stringResource(R.string.supplement_history_title, supplement.name),
        empty = intakes.isEmpty(),
        onDismiss = onDismiss,
        onAdd = { editing = null; showEditor = true }
    ) {
        items(intakes.sortedByDescending { it.date }, key = { it.id }) { intake ->
            LogRow(
                date = intake.date,
                status = stringResource(if (intake.taken) R.string.taken_status else R.string.not_taken_status),
                detail = listOfNotNull(intake.dose, intake.brand, intake.notes).joinToString(" · "),
                onEdit = { editing = intake; showEditor = true },
                onDelete = { onDelete(intake.id) }
            )
        }
    }
}

@Composable
private fun LogHistoryDialog(
    title: String,
    empty: Boolean,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.add_manual_log), modifier = Modifier.padding(start = 8.dp))
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (empty) item { Text(stringResource(R.string.no_logs), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    content()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun LogRow(date: Long, status: String, detail: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val local = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(status, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.log_date_time, local.toLocalDate().format(logDateFormatter), local.toLocalTime().format(logTimeFormatter)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit)) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProtocolCompletionEditorDialog(
    protocol: ProtocolItem,
    existing: ProtocolCompletion?,
    onDismiss: () -> Unit,
    onSave: (ProtocolCompletion) -> Unit
) {
    val initial = existing?.date?.let(::localDateTime) ?: LocalDateTime.now()
    var date by remember(existing?.id) { mutableStateOf(initial.toLocalDate().format(logDateFormatter)) }
    var hour by remember(existing?.id) { mutableStateOf(initial.hour.toString()) }
    var minute by remember(existing?.id) { mutableStateOf(initial.minute.toString()) }
    var completed by remember(existing?.id) { mutableStateOf(existing?.completed ?: true) }
    var goal by remember(existing?.id) { mutableStateOf(existing?.goal ?: protocol.goal.orEmpty()) }
    var intervention by remember(existing?.id) { mutableStateOf(existing?.intervention ?: protocol.intervention.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var dateError by remember(existing?.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.new_protocol_log else R.string.edit_protocol_log)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateTimeFields(date, { date = it; dateError = false }, hour, { hour = it }, minute, { minute = it }, dateError)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = completed, onCheckedChange = { completed = it })
                    Text(stringResource(R.string.completed_status))
                }
                OutlinedTextField(goal, { goal = it }, label = { Text(stringResource(R.string.goal_field)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(intervention, { intervention = it }, label = { Text(stringResource(R.string.intervention_field)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes_field)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = {
                val timestamp = parseTimestamp(date, hour, minute)
                if (timestamp == null) dateError = true else onSave(
                    (existing ?: ProtocolCompletion(protocolId = protocol.id)).copy(
                        protocolId = protocol.id,
                        date = timestamp,
                        completed = completed,
                        goal = goal.trim().takeIf(String::isNotEmpty),
                        intervention = intervention.trim().takeIf(String::isNotEmpty),
                        notes = notes.trim().takeIf(String::isNotEmpty)
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun SupplementIntakeEditorDialog(
    supplement: Supplement,
    existing: SupplementIntake?,
    onDismiss: () -> Unit,
    onSave: (SupplementIntake) -> Unit
) {
    val initial = existing?.date?.let(::localDateTime) ?: LocalDateTime.now()
    var date by remember(existing?.id) { mutableStateOf(initial.toLocalDate().format(logDateFormatter)) }
    var hour by remember(existing?.id) { mutableStateOf(initial.hour.toString()) }
    var minute by remember(existing?.id) { mutableStateOf(initial.minute.toString()) }
    var taken by remember(existing?.id) { mutableStateOf(existing?.taken ?: true) }
    var dose by remember(existing?.id) { mutableStateOf(existing?.dose ?: supplement.dose.orEmpty()) }
    var brand by remember(existing?.id) { mutableStateOf(existing?.brand ?: supplement.brand.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var dateError by remember(existing?.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.new_supplement_log else R.string.edit_supplement_log)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateTimeFields(date, { date = it; dateError = false }, hour, { hour = it }, minute, { minute = it }, dateError)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = taken, onCheckedChange = { taken = it })
                    Text(stringResource(R.string.taken_status))
                }
                OutlinedTextField(dose, { dose = it }, label = { Text(stringResource(R.string.dose_field)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.brand_field)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes_field)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = {
                val timestamp = parseTimestamp(date, hour, minute)
                if (timestamp == null) dateError = true else onSave(
                    (existing ?: SupplementIntake(supplementId = supplement.id)).copy(
                        supplementId = supplement.id,
                        date = timestamp,
                        taken = taken,
                        dose = dose.trim().takeIf(String::isNotEmpty),
                        brand = brand.trim().takeIf(String::isNotEmpty),
                        notes = notes.trim().takeIf(String::isNotEmpty)
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun DateTimeFields(
    date: String,
    onDateChange: (String) -> Unit,
    hour: String,
    onHourChange: (String) -> Unit,
    minute: String,
    onMinuteChange: (String) -> Unit,
    dateError: Boolean
) {
    OutlinedTextField(
        value = date,
        onValueChange = onDateChange,
        label = { Text(stringResource(R.string.date_field)) },
        isError = dateError,
        supportingText = if (dateError) ({ Text(stringResource(R.string.invalid_date)) }) else null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(hour, { onHourChange(it.filter(Char::isDigit).take(2)) }, label = { Text(stringResource(R.string.hour_field)) }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(minute, { onMinuteChange(it.filter(Char::isDigit).take(2)) }, label = { Text(stringResource(R.string.minute_field)) }, modifier = Modifier.weight(1f), singleLine = true)
    }
}

private fun localDateTime(timestamp: Long): LocalDateTime =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()

private fun parseTimestamp(date: String, hour: String, minute: String): Long? = runCatching {
    val parsedDate = LocalDate.parse(date.trim(), logDateFormatter)
    parsedDate.atTime(hour.toIntOrNull()?.coerceIn(0, 23) ?: 0, minute.toIntOrNull()?.coerceIn(0, 59) ?: 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()
