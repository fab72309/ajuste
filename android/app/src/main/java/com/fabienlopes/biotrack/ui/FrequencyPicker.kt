package com.fabienlopes.biotrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.Frequency
import com.fabienlopes.biotrack.data.FrequencyKind
import com.fabienlopes.biotrack.data.ItemReminderConfig

private enum class FrequencyMode { DAILY, MULTIPLE, AS_NEEDED, SPECIFIC_DAYS }

private fun Frequency.mode(): FrequencyMode = when {
    kind == FrequencyKind.DAILY -> FrequencyMode.DAILY
    kind == FrequencyKind.TIMES_PER_DAY -> FrequencyMode.MULTIPLE
    isAsNeeded -> FrequencyMode.AS_NEEDED
    else -> FrequencyMode.SPECIFIC_DAYS
}

@Composable
internal fun localizedFrequencyLabel(frequency: Frequency): String {
    if (frequency.kind == FrequencyKind.DAILY || (frequency.kind == FrequencyKind.TIMES_PER_DAY && frequency.timesPerDay <= 1)) {
        return stringResource(R.string.frequency_daily)
    }
    if (frequency.kind == FrequencyKind.TIMES_PER_DAY) {
        return stringResource(R.string.frequency_times_per_day_short, frequency.timesPerDay)
    }
    if (frequency.days.isEmpty()) return stringResource(R.string.frequency_as_needed)
    return frequency.days.mapNotNull { day -> localizedWeekdayLabel(day).takeIf { it.isNotBlank() } }.joinToString(", ")
}

@Composable
internal fun localizedWeekdayLabel(day: Int?): String {
    val labels = listOf(
        R.string.weekday_monday,
        R.string.weekday_tuesday,
        R.string.weekday_wednesday,
        R.string.weekday_thursday,
        R.string.weekday_friday,
        R.string.weekday_saturday,
        R.string.weekday_sunday
    )
    return day?.let { labels.getOrNull(it - 1) }?.let { stringResource(it) }.orEmpty()
}

@Composable
fun FrequencyPicker(
    frequency: Frequency,
    onFrequencyChange: (Frequency) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = frequency.mode()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.frequency_title), style = MaterialTheme.typography.titleSmall)
        FrequencyChoice(
            selected = selected == FrequencyMode.DAILY,
            title = stringResource(R.string.frequency_daily),
            hint = stringResource(R.string.frequency_daily_hint),
            onClick = { onFrequencyChange(Frequency.daily()) }
        )
        FrequencyChoice(
            selected = selected == FrequencyMode.MULTIPLE,
            title = stringResource(R.string.frequency_multiple),
            hint = stringResource(R.string.frequency_multiple_hint),
            onClick = {
                onFrequencyChange(Frequency.timesPerDay(frequency.timesPerDay.takeIf { it > 1 } ?: 2))
            }
        )
        FrequencyChoice(
            selected = selected == FrequencyMode.AS_NEEDED,
            title = stringResource(R.string.frequency_as_needed),
            hint = stringResource(R.string.frequency_as_needed_hint),
            onClick = { onFrequencyChange(Frequency.weekly(emptyList())) }
        )
        FrequencyChoice(
            selected = selected == FrequencyMode.SPECIFIC_DAYS,
            title = stringResource(R.string.frequency_specific_days),
            hint = stringResource(R.string.frequency_specific_hint),
            onClick = {
                onFrequencyChange(Frequency.weekly(frequency.days.takeIf { it.isNotEmpty() } ?: listOf(1)))
            }
        )

        if (selected == FrequencyMode.MULTIPLE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onFrequencyChange(Frequency.timesPerDay((frequency.timesPerDay - 1).coerceAtLeast(2))) },
                    enabled = frequency.timesPerDay > 2
                ) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease_times))
                }
                Text(
                    stringResource(R.string.times_per_day, frequency.timesPerDay.coerceAtLeast(2)),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(onClick = { onFrequencyChange(Frequency.timesPerDay((frequency.timesPerDay + 1).coerceAtMost(12))) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase_times))
                }
            }
        }
        if (selected == FrequencyMode.SPECIFIC_DAYS) {
            WeekdayPicker(days = frequency.days, onDaysChange = { days ->
                onFrequencyChange(Frequency.weekly(days.ifEmpty { listOf(1) }))
            })
        }
    }
}

@Composable
private fun FrequencyChoice(selected: Boolean, title: String, hint: String, onClick: () -> Unit) {
    Column {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(title) },
            modifier = Modifier.fillMaxWidth()
        )
        if (selected) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WeekdayPicker(days: List<Int>, onDaysChange: (List<Int>) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf(
        R.string.weekday_monday,
        R.string.weekday_tuesday,
        R.string.weekday_wednesday,
        R.string.weekday_thursday,
        R.string.weekday_friday,
        R.string.weekday_saturday,
        R.string.weekday_sunday
    )
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items((1..7).toList()) { day ->
            FilterChip(
                selected = day in days,
                onClick = {
                    val next = if (day in days) days - day else days + day
                    onDaysChange(next.distinct().sorted())
                },
                label = { Text(stringResource(labels[day - 1])) }
            )
        }
    }
}

@Composable
fun CustomReminderFields(
    config: ItemReminderConfig,
    onConfigChange: (ItemReminderConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = config.title.orEmpty(),
            onValueChange = { onConfigChange(config.copy(title = it.takeIf(String::isNotBlank))) },
            label = { Text(stringResource(R.string.reminder_title_field)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = config.notes.orEmpty(),
            onValueChange = { onConfigChange(config.copy(notes = it.takeIf(String::isNotBlank))) },
            label = { Text(stringResource(R.string.reminder_notes_field)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = config.hour.toString(),
                onValueChange = { value ->
                    value.filter(Char::isDigit).take(2).toIntOrNull()?.let { onConfigChange(config.copy(hour = it.coerceIn(0, 23))) }
                },
                label = { Text(stringResource(R.string.hour_field)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = config.minute.toString(),
                onValueChange = { value ->
                    value.filter(Char::isDigit).take(2).toIntOrNull()?.let { onConfigChange(config.copy(minute = it.coerceIn(0, 59))) }
                },
                label = { Text(stringResource(R.string.minute_field)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        WeekdayPicker(days = config.weekdays, onDaysChange = { onConfigChange(config.copy(weekdays = it)) })
    }
}
