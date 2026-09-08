package com.moneyplanner.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moneyplanner.ui.components.SectionCard

/**
 * The hub for everything that does not earn a place in the bottom bar.
 *
 * Entries are grouped by what the user is trying to do rather than by how the app is
 * built, so "money going out" holds loans, bills and cards together even though each is
 * a separate part of the system.
 */
@Composable
fun MoreScreen(
    onOpenRecentActivity: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenIncome: () -> Unit,
    onOpenEmis: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenAnnual: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenEmergencyFund: () -> Unit,
    onOpenVehicles: () -> Unit,
    onOpenFamily: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenAffordability: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenSmsImport: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("More", style = MaterialTheme.typography.titleLarge)
        }

        item {
            SectionCard(title = "Where your money is") {
                MoreRow(
                    Icons.Default.History,
                    "Recent activity",
                    "The last 10 entries, to correct or remove one",
                    onOpenRecentActivity
                )
                MoreRow(
                    Icons.Default.AccountBalanceWallet,
                    "Accounts",
                    "Balances per account, and moving money between them",
                    onOpenAccounts
                )
            }
        }

        item {
            SectionCard(title = "People") {
                MoreRow(
                    Icons.Default.Groups,
                    "People and splits",
                    "Who owes you, whom you owe, and shared bills",
                    onOpenPeople
                )
            }
        }

        item {
            SectionCard(title = "Save typing") {
                MoreRow(
                    Icons.AutoMirrored.Filled.Message,
                    "Import from SMS",
                    "Turn bank alerts into entries without typing them",
                    onOpenSmsImport
                )
            }
        }

        item {
            SectionCard(title = "Money coming in") {
                MoreRow(Icons.Default.Work, "Income", "Salary and other earnings", onOpenIncome)
            }
        }

        item {
            SectionCard(title = "Money going out") {
                MoreRow(
                    Icons.Default.AccountBalance,
                    "EMIs and loans",
                    "Installments and remaining tenure",
                    onOpenEmis
                )
                MoreRow(
                    Icons.Default.EventRepeat,
                    "Recurring bills",
                    "Rent, electricity, subscriptions",
                    onOpenBills
                )
                MoreRow(
                    Icons.Default.CreditCard,
                    "Credit cards",
                    "Outstanding and due dates",
                    onOpenCards
                )
                MoreRow(
                    Icons.Default.Today,
                    "Yearly expenses",
                    "Insurance, school fees, property tax",
                    onOpenAnnual
                )
            }
        }

        item {
            SectionCard(title = "Limits") {
                MoreRow(
                    Icons.Default.PieChart,
                    "Budgets",
                    "Monthly limits, and whether you are on pace",
                    onOpenBudgets
                )
            }
        }

        item {
            SectionCard(title = "Saving") {
                MoreRow(
                    Icons.Default.Savings,
                    "Savings goals",
                    "Targets and what each needs monthly",
                    onOpenGoals
                )
                MoreRow(
                    Icons.Default.Shield,
                    "Emergency fund",
                    "Built from your own essential costs",
                    onOpenEmergencyFund
                )
            }
        }

        item {
            SectionCard(title = "Household") {
                MoreRow(
                    Icons.Default.DirectionsCar,
                    "Vehicles",
                    "What each bike or car really costs",
                    onOpenVehicles
                )
                MoreRow(
                    Icons.Default.People,
                    "Family",
                    "What is being spent on each person",
                    onOpenFamily
                )
            }
        }

        item {
            SectionCard(title = "Understand") {
                MoreRow(
                    Icons.Default.CalendarMonth,
                    "Money calendar",
                    "Every dated payment in one place",
                    onOpenCalendar
                )
                MoreRow(
                    Icons.Default.Assessment,
                    "Reports",
                    "Where your money actually went",
                    onOpenReports
                )
                MoreRow(
                    Icons.Default.ShoppingCart,
                    "Can I afford it?",
                    "Check a purchase against what is coming",
                    onOpenAffordability
                )
                MoreRow(
                    Icons.AutoMirrored.Filled.Chat,
                    "Ask about your money",
                    "Answered on your phone from your own records",
                    onOpenAssistant
                )
            }
        }

        item {
            SectionCard {
                MoreRow(
                    Icons.Default.Settings,
                    "Settings",
                    "Reminders, security, backup and categories",
                    onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
