package com.fabienlopes.biotrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.FrequencyKind
import com.fabienlopes.biotrack.data.ItemReminderConfig
import com.fabienlopes.biotrack.data.ProtocolItem
import com.fabienlopes.biotrack.data.Supplement

@Composable
fun ProtocolEditorDialog(
    initial: ProtocolItem,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProtocolItem, Boolean) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var detail by remember(initial.id) { mutableStateOf(initial.detail.orEmpty()) }
    var goal by remember(initial.id) { mutableStateOf(initial.goal.orEmpty()) }
    var intervention by remember(initial.id) { mutableStateOf(initial.intervention.orEmpty()) }
    var category by remember(initial.id) { mutableStateOf(initial.category.orEmpty()) }
    var minutes by remember(initial.id) { mutableStateOf((initial.targetMinutes ?: 10).toString()) }
    var hour by remember(initial.id) { mutableStateOf((initial.preferredHour ?: 8).toString()) }
    var minute by remember(initial.id) { mutableStateOf((initial.preferredMinute ?: 0).toString()) }
    var frequency by remember(initial.id) { mutableStateOf(initial.frequency) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes.orEmpty()) }
    var active by remember(initial.id) { mutableStateOf(initial.active) }
    var remindersEnabled by remember(initial.id) {
        mutableStateOf(initial.remindersEnabled || initial.customReminder?.enabled == true)
    }
    var customReminderEnabled by remember(initial.id) { mutableStateOf(initial.customReminder != null) }
    var reminderConfig by remember(initial.id) {
        mutableStateOf(initial.customReminder ?: ItemReminderConfig(hour = initial.preferredHour ?: 8, minute = initial.preferredMinute ?: 0))
    }
    var saveAsTemplate by remember(initial.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isEditing) R.string.edit_protocol else R.string.new_protocol)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 590.dp).verticalScroll(rememberScrollState()).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(detail, { detail = it }, label = { Text(stringResource(R.string.detail_field)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(goal, { goal = it }, label = { Text(stringResource(R.string.goal_field)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(intervention, { intervention = it }, label = { Text(stringResource(R.string.intervention_field)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.category_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.minutes_field)) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.hour_field)) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.minute_field)) }, modifier = Modifier.weight(1f), singleLine = true)
                }
                FrequencyPicker(frequency = frequency, onFrequencyChange = { frequency = it })
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes_field)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                ToggleRow(stringResource(R.string.item_active), active) { active = it }
                ToggleRow(stringResource(R.string.reminders_enabled), remindersEnabled) { remindersEnabled = it }
                ToggleRow(stringResource(R.string.custom_reminder), customReminderEnabled) { customReminderEnabled = it }
                if (customReminderEnabled) {
                    CustomReminderFields(reminderConfig, onConfigChange = { reminderConfig = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveAsTemplate, onCheckedChange = { saveAsTemplate = it })
                    Text(stringResource(R.string.save_as_template))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val saved = initial.copy(
                        name = name.trim(),
                        detail = detail.trim().takeIf(String::isNotEmpty),
                        goal = goal.trim().takeIf(String::isNotEmpty),
                        intervention = intervention.trim().takeIf(String::isNotEmpty),
                        category = category.trim().takeIf(String::isNotEmpty),
                        targetMinutes = minutes.toIntOrNull()?.coerceAtLeast(1),
                        preferredHour = hour.toIntOrNull()?.coerceIn(0, 23),
                        preferredMinute = minute.toIntOrNull()?.coerceIn(0, 59),
                        frequency = frequency,
                        notes = notes.trim().takeIf(String::isNotEmpty),
                        active = active,
                        remindersEnabled = remindersEnabled,
                        customReminder = when {
                            customReminderEnabled -> reminderConfig.copy(enabled = remindersEnabled)
                            remindersEnabled -> ItemReminderConfig(
                                enabled = true,
                                title = name.trim().takeIf(String::isNotEmpty),
                                hour = hour.toIntOrNull()?.coerceIn(0, 23) ?: 8,
                                minute = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                                weekdays = emptyList()
                            )
                            else -> null
                        }
                    )
                    onSave(saved, saveAsTemplate)
                },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun SupplementEditorDialog(
    initial: Supplement,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (Supplement, Boolean) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var brand by remember(initial.id) { mutableStateOf(initial.brand.orEmpty()) }
    var dose by remember(initial.id) { mutableStateOf(initial.dose.orEmpty()) }
    var category by remember(initial.id) { mutableStateOf(initial.category.orEmpty()) }
    var context by remember(initial.id) { mutableStateOf(initial.timeContext.orEmpty()) }
    var hour by remember(initial.id) { mutableStateOf((initial.timeOfDay?.div(60) ?: 8).toString()) }
    var minute by remember(initial.id) { mutableStateOf((initial.timeOfDay?.rem(60) ?: 0).toString()) }
    var frequency by remember(initial.id) { mutableStateOf(initial.frequency) }
    var duration by remember(initial.id) { mutableStateOf(initial.durationNote.orEmpty()) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes.orEmpty()) }
    var active by remember(initial.id) { mutableStateOf(initial.active) }
    var customReminderEnabled by remember(initial.id) { mutableStateOf(initial.customReminder != null) }
    var reminderActive by remember(initial.id) { mutableStateOf(initial.customReminder?.enabled ?: true) }
    var reminderConfig by remember(initial.id) {
        mutableStateOf(initial.customReminder ?: ItemReminderConfig(hour = initial.timeOfDay?.div(60) ?: 8, minute = initial.timeOfDay?.rem(60) ?: 0))
    }
    var saveAsTemplate by remember(initial.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isEditing) R.string.edit_supplement else R.string.new_supplement)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.brand_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(dose, { dose = it }, label = { Text(stringResource(R.string.dose_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.category_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(context, { context = it }, label = { Text(stringResource(R.string.time_context_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.hour_field)) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.minute_field)) }, modifier = Modifier.weight(1f), singleLine = true)
                }
                FrequencyPicker(frequency = frequency, onFrequencyChange = { frequency = it })
                OutlinedTextField(duration, { duration = it }, label = { Text(stringResource(R.string.duration_field)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes_field)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                ToggleRow(stringResource(R.string.item_active), active) { active = it }
                ToggleRow(stringResource(R.string.custom_reminder), customReminderEnabled) { customReminderEnabled = it }
                if (customReminderEnabled) {
                    ToggleRow(stringResource(R.string.reminders_enabled), reminderActive) { reminderActive = it }
                    CustomReminderFields(reminderConfig, onConfigChange = { reminderConfig = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveAsTemplate, onCheckedChange = { saveAsTemplate = it })
                    Text(stringResource(R.string.save_as_template))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val timeOfDay = (hour.toIntOrNull()?.coerceIn(0, 23) ?: 8) * 60 +
                        (minute.toIntOrNull()?.coerceIn(0, 59) ?: 0)
                    val saved = initial.copy(
                        name = name.trim(),
                        brand = brand.trim().takeIf(String::isNotEmpty),
                        dose = dose.trim().takeIf(String::isNotEmpty),
                        category = category.trim().takeIf(String::isNotEmpty),
                        timeContext = context.trim().takeIf(String::isNotEmpty),
                        timeOfDay = timeOfDay,
                        frequency = frequency,
                        timesPerDay = if (frequency.kind == FrequencyKind.TIMES_PER_DAY) frequency.effectiveTimesPerDay else null,
                        daysOfWeek = if (frequency.kind == FrequencyKind.WEEKLY) frequency.days else null,
                        durationNote = duration.trim().takeIf(String::isNotEmpty),
                        notes = notes.trim().takeIf(String::isNotEmpty),
                        active = active,
                        customReminder = reminderConfig.copy(enabled = reminderActive).takeIf { customReminderEnabled }
                    )
                    onSave(saved, saveAsTemplate)
                },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
