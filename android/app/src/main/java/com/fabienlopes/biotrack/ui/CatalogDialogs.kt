package com.fabienlopes.biotrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.CustomProtocolTemplate
import com.fabienlopes.biotrack.data.CustomSupplementTemplate
import com.fabienlopes.biotrack.data.ProtocolCatalog
import com.fabienlopes.biotrack.data.ProtocolItem
import com.fabienlopes.biotrack.data.Supplement
import com.fabienlopes.biotrack.data.SupplementCatalog

@Composable
fun ProtocolCatalogDialog(
    customTemplates: List<CustomProtocolTemplate>,
    onDismiss: () -> Unit,
    onBlank: () -> Unit,
    onSelect: (ProtocolItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    val all = ProtocolCatalog.available(customTemplates)
    val filtered = ProtocolCatalog.available(customTemplates, query, category)
    val categories = all.map { it.category }.distinct().sorted()
    CatalogDialogScaffold(
        title = stringResource(R.string.protocol_catalog_title),
        query = query,
        onQueryChange = { query = it },
        categories = categories,
        category = category,
        onCategoryChange = { category = it },
        hasCustomTemplates = customTemplates.isNotEmpty(),
        isEmpty = filtered.isEmpty(),
        onDismiss = onDismiss,
        onBlank = onBlank
    ) {
        items(filtered, key = { it.id }) { entry ->
            CatalogEntryCard(
                name = entry.name,
                category = entry.category,
                detail = entry.goal,
                isCustom = entry.isCustom,
                onClick = {
                    val custom = customTemplates.firstOrNull { template -> entry.id == "custom-${template.id}" }
                    onSelect(
                        custom?.let { template ->
                            ProtocolItem(
                                name = template.name,
                                detail = template.detail,
                                goal = template.goal,
                                intervention = template.intervention,
                                category = template.category,
                                targetMinutes = template.minutes,
                                frequency = template.frequency,
                                preferredHour = template.hour,
                                preferredMinute = template.minute,
                                notes = template.notes,
                                customReminder = template.customReminder,
                                remindersEnabled = template.customReminder?.enabled == true
                            )
                        } ?: entry.toProtocolItem()
                    )
                }
            )
        }
    }
}

@Composable
fun SupplementCatalogDialog(
    customTemplates: List<CustomSupplementTemplate>,
    onDismiss: () -> Unit,
    onBlank: () -> Unit,
    onSelect: (Supplement) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    val all = SupplementCatalog.available(customTemplates)
    val filtered = SupplementCatalog.available(customTemplates, query, category)
    val categories = all.flatMap { it.categories }.distinct().sorted()
    CatalogDialogScaffold(
        title = stringResource(R.string.supplement_catalog_title),
        query = query,
        onQueryChange = { query = it },
        categories = categories,
        category = category,
        onCategoryChange = { category = it },
        hasCustomTemplates = customTemplates.isNotEmpty(),
        isEmpty = filtered.isEmpty(),
        onDismiss = onDismiss,
        onBlank = onBlank
    ) {
        items(filtered, key = { it.id }) { entry ->
            CatalogEntryCard(
                name = entry.name,
                category = entry.categories.joinToString(" · "),
                detail = listOfNotNull(entry.dose, entry.timeContext).joinToString(" · "),
                isCustom = entry.isCustom,
                onClick = { onSelect(entry.toSupplement()) }
            )
        }
    }
}

@Composable
private fun CatalogDialogScaffold(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    categories: List<String>,
    category: String?,
    onCategoryChange: (String?) -> Unit,
    hasCustomTemplates: Boolean,
    isEmpty: Boolean,
    onDismiss: () -> Unit,
    onBlank: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.catalog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = category == null,
                            onClick = { onCategoryChange(null) },
                            label = { Text(stringResource(R.string.all_filter)) }
                        )
                    }
                    items(categories) { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { onCategoryChange(item) },
                            label = { Text(item) }
                        )
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isEmpty) {
                        item { Text(stringResource(R.string.catalog_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        content()
                    }
                    if (!hasCustomTemplates) {
                        item {
                            Text(
                                stringResource(R.string.custom_templates_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onBlank) { Text(stringResource(R.string.blank_form)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun CatalogEntryCard(name: String, category: String, detail: String, isCustom: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    stringResource(if (isCustom) R.string.custom_template else R.string.built_in_template),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
