package com.moneyplanner.ui.screens.assets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.domain.model.Relation
import com.moneyplanner.domain.model.VehicleType
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.InitialsAvatar
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.SummaryRow

/**
 * Vehicles, and everything each one costs.
 *
 * A bike or a car spreads its cost across several places: an EMI, fuel, insurance and
 * repairs. This screen pulls those together into the monthly figure people actually want,
 * which is what the vehicle really costs to keep.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    onBack: () -> Unit,
    onAddVehicle: () -> Unit,
    viewModel: VehiclesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddVehicle,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add vehicle") }
            )
        }
    ) { padding ->
        if (state.vehicles.isEmpty()) {
            EmptyState(
                icon = Icons.Default.DirectionsCar,
                title = "No vehicles added",
                message = "Add your bike or car to see what it costs you every month once " +
                    "fuel, service, insurance and its EMI are put together.",
                actionLabel = "Add a vehicle",
                onAction = onAddVehicle,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "All vehicles") {
                    SummaryRow(
                        label = "Estimated cost each month",
                        amount = state.totalMonthlyCost,
                        supporting = "EMIs, yearly costs spread monthly, and recent running costs",
                        emphasise = true
                    )
                }
            }

            items(state.vehicles, key = { it.vehicle.id }) { row ->
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.vehicle.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append(row.vehicle.type.label)
                                    row.vehicle.registrationNumber?.let { append(" · $it") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.delete(row.vehicle.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete vehicle")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    if (row.emiMonthly.isPositive) SummaryRow("Loan installment", row.emiMonthly)
                    if (row.annualMonthly.isPositive) {
                        SummaryRow(
                            label = "Yearly costs",
                            amount = row.annualMonthly,
                            supporting = "Insurance and similar, spread over twelve months"
                        )
                    }
                    if (row.runningMonthly.isPositive) {
                        SummaryRow(
                            label = "Running costs",
                            amount = row.runningMonthly,
                            supporting = "Average of fuel, service and repairs you have recorded"
                        )
                    }

                    if (row.monthlyCost.isZero) {
                        Text(
                            "No costs recorded against this vehicle yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SummaryRow("Roughly each month", row.monthlyCost, emphasise = true)
                        Text(
                            "That is about ${IndianFormat.format(row.monthlyCost * 12)} a year.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditorScreen(
    onBack: () -> Unit,
    viewModel: VehicleEditorViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add vehicle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LabelledTextField(
                value = form.name,
                onValueChange = viewModel::updateName,
                label = "Name",
                supportingText = "For example, Splendor or Swift",
                isError = form.nameError != null,
                errorMessage = form.nameError
            )
            ChipSelector(
                label = "Type",
                options = VehicleType.entries,
                selected = form.type,
                onSelect = viewModel::updateType,
                optionLabel = { it.label }
            )
            LabelledTextField(
                value = form.registration,
                onValueChange = viewModel::updateRegistration,
                label = "Registration number (optional)",
                imeAction = ImeAction.Done
            )
            Spacer(Modifier.height(8.dp))
            PrimaryActionButton(
                text = "Add vehicle",
                onClick = { viewModel.save(onBack) },
                enabled = form.canSave
            )
        }
    }
}

/**
 * Family members, and what is being spent on each of them.
 *
 * Households often want to know what a child or a parent costs across a month, which is
 * something a category alone cannot answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    onBack: () -> Unit,
    viewModel: FamilyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add member") }
            )
        }
    ) { padding ->
        if (state.members.isEmpty()) {
            EmptyState(
                icon = Icons.Default.People,
                title = "No family members yet",
                message = "Add the people in your household to see what is being spent on " +
                    "each of them.",
                actionLabel = "Add a member",
                onAction = { showAdd = true },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SectionCard(title = "This month") {
                        SummaryRow("Spent on the family", state.totalThisMonth, emphasise = true)
                    }
                }
                items(state.members, key = { it.member.id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InitialsAvatar(row.member.name)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.member.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                row.member.relation.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MoneyText(row.spentThisMonth, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "this month",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!row.member.isSelf) {
                            IconButton(onClick = { viewModel.delete(row.member.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove member")
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showAdd) {
        AddFamilyMemberDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, relation ->
                viewModel.add(name, relation)
                showAdd = false
            }
        )
    }
}

@Composable
private fun AddFamilyMemberDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Relation) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf(Relation.SON) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add family member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabelledTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name"
                )
                ChipSelector(
                    label = "Relationship",
                    options = listOf(
                        Relation.WIFE, Relation.HUSBAND, Relation.SON, Relation.DAUGHTER,
                        Relation.FATHER, Relation.MOTHER, Relation.OTHER
                    ),
                    selected = relation,
                    onSelect = { relation = it },
                    optionLabel = { it.label }
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onAdd(name, relation) },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
