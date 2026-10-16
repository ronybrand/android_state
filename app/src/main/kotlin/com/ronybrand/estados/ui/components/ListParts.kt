package com.ronybrand.estados.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronybrand.estados.core.formatDate
import com.ronybrand.estados.data.FederativeUnit
import com.ronybrand.estados.state.SortDirection
import com.ronybrand.estados.state.SortField
import com.ronybrand.estados.state.SortState
import com.ronybrand.estados.ui.AppColors

@Composable
fun StateListItem(
    unit: FederativeUnit,
    onEdit: (FederativeUnit) -> Unit,
    onDelete: (FederativeUnit) -> Unit,
    modifier: Modifier = Modifier,
    deleting: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(unit.abbreviation, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.Brand)
                Text(unit.name, fontSize = 17.sp)
            }
            Text(
                "Created: ${formatDate(unit.createdAt)}",
                fontSize = 12.sp,
                color = AppColors.Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text("Updated: ${formatDate(unit.updatedAt)}", fontSize = 12.sp, color = AppColors.Muted)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The visible text is "Edit"; a screen reader needs to know which state it edits.
                Button(
                    onClick = { onEdit(unit) },
                    modifier = Modifier.testTag("edit-${unit.id}").clearAndSetSemantics {
                        contentDescription = "Edit ${unit.abbreviation}"
                        role = Role.Button
                        onClick {
                            onEdit(unit)
                            true
                        }
                    },
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Edit", modifier = Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = { onDelete(unit) },
                    enabled = !deleting,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Danger),
                    modifier = Modifier.testTag("delete-${unit.id}").clearAndSetSemantics {
                        contentDescription = "Delete ${unit.abbreviation}"
                        role = Role.Button
                        if (deleting) {
                            disabled()
                        } else {
                            onClick {
                                onDelete(unit)
                                true
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Delete", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBar(sort: SortState, onSort: (SortField) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sort by", color = AppColors.Muted)
        for (field in SortField.entries) {
            val selected = sort.active == field
            val ascending = sort.direction == SortDirection.Ascending
            // Read as "Name, ascending": the visual arrow alone says nothing to a screen reader.
            val direction = if (ascending) "ascending" else "descending"
            val description = if (selected) "${field.label}, $direction" else field.label
            FilterChip(
                selected = selected,
                onClick = { onSort(field) },
                label = { Text(field.label) },
                leadingIcon = if (selected) {
                    { Text(if (ascending) "↑" else "↓", color = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
                modifier = Modifier.testTag("sort-${field.wire}").clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    this.selected = selected
                    onClick {
                        onSort(field)
                        true
                    }
                },
            )
        }
    }
}
