package com.moneyplanner.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.moneyplanner.ui.screens.accounts.AccountsScreen
import com.moneyplanner.ui.screens.activity.RecentActivityScreen
import com.moneyplanner.ui.screens.afford.AffordabilityScreen
import com.moneyplanner.ui.screens.assistant.AssistantScreen
import com.moneyplanner.domain.nlp.AssistantAction
import com.moneyplanner.ui.screens.assets.FamilyScreen
import com.moneyplanner.ui.screens.assets.VehicleEditorScreen
import com.moneyplanner.ui.screens.assets.VehiclesScreen
import com.moneyplanner.ui.screens.budgets.BudgetsScreen
import com.moneyplanner.ui.screens.calendar.CalendarScreen
import com.moneyplanner.ui.screens.commitments.AnnualEditorScreen
import com.moneyplanner.ui.screens.commitments.AnnualScreen
import com.moneyplanner.ui.screens.commitments.BillEditorScreen
import com.moneyplanner.ui.screens.commitments.BillsScreen
import com.moneyplanner.ui.screens.commitments.CardEditorScreen
import com.moneyplanner.ui.screens.commitments.CardsScreen
import com.moneyplanner.ui.screens.dashboard.DashboardScreen
import com.moneyplanner.ui.screens.emi.EmiDetailScreen
import com.moneyplanner.ui.screens.emi.EmiEditorScreen
import com.moneyplanner.ui.screens.emi.EmiListScreen
import com.moneyplanner.ui.screens.expenses.ExpenseEditorScreen
import com.moneyplanner.ui.screens.expenses.TransactionsScreen
import com.moneyplanner.ui.screens.forecast.ForecastScreen
import com.moneyplanner.ui.screens.goals.EmergencyFundScreen
import com.moneyplanner.ui.screens.goals.GoalDetailScreen
import com.moneyplanner.ui.screens.goals.GoalEditorScreen
import com.moneyplanner.ui.screens.goals.GoalsScreen
import com.moneyplanner.ui.screens.income.IncomeReceiptScreen
import com.moneyplanner.ui.screens.income.IncomeScreen
import com.moneyplanner.ui.screens.income.IncomeSourceEditorScreen
import com.moneyplanner.ui.screens.more.MoreScreen
import com.moneyplanner.ui.screens.people.AddPersonScreen
import com.moneyplanner.ui.screens.people.ObligationEditorScreen
import com.moneyplanner.ui.screens.people.PeopleScreen
import com.moneyplanner.ui.screens.people.PersonDetailScreen
import com.moneyplanner.ui.screens.people.SharedExpenseScreen
import com.moneyplanner.ui.screens.reports.ReportsScreen
import com.moneyplanner.ui.screens.search.SearchScreen
import com.moneyplanner.ui.screens.sms.SmsImportScreen
import com.moneyplanner.ui.screens.settings.CategoriesScreen
import com.moneyplanner.ui.screens.settings.SettingsScreen

/**
 * The navigation graph.
 *
 * The bottom bar carries the five things people come back to daily. Everything else is
 * reached through the More hub or from the screen it belongs to, which keeps the bar
 * readable rather than turning it into a menu of everything the app can do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyPlannerNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()
    val showBars = currentRoute in topLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBars) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == destination.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    // Keep a single copy of each tab and restore what the
                                    // user was looking at when they come back to it.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.icon
                                    },
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.DASHBOARD || currentRoute == Routes.TRANSACTIONS) {
                FloatingActionButton(onClick = { navController.navigate(Routes.ADD_EXPENSE) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add expense")
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding)
        ) {
            // ---- Bottom bar destinations ---------------------------------------

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onAddExpense = { navController.navigate(Routes.ADD_EXPENSE) },
                    onAddIncome = { navController.navigate(Routes.ADD_INCOME_RECEIPT) },
                    onOpenEmis = { navController.navigate(Routes.PLANS) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenForecast = { navController.navigate(Routes.FORECAST) },
                    onOpenPeople = { navController.navigate(Routes.PEOPLE) },
                    onOpenCards = { navController.navigate(Routes.CARDS) },
                    onOpenGoals = { navController.navigate(Routes.GOALS) },
                    onOpenEmergencyFund = { navController.navigate(Routes.EMERGENCY_FUND) },
                    onOpenCalendar = { navController.navigate(Routes.CALENDAR) },
                    onOpenTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                    onOpenAffordability = { navController.navigate(Routes.AFFORDABILITY) },
                    onOpenAssistant = { navController.navigate(Routes.ASSISTANT) },
                    onOpenBudgets = { navController.navigate(Routes.BUDGETS) }
                )
            }

            composable(Routes.RECENT_ACTIVITY) {
                RecentActivityScreen(
                    onBack = { navController.popBackStack() },
                    onEditExpense = { id -> navController.navigate(Routes.editExpense(id)) },
                    onEditIncome = { id ->
                        navController.navigate(Routes.editIncomeReceipt(id))
                    },
                    onEditSettlement = { personId, settlementId ->
                        navController.navigate(
                            Routes.personDetailEditingSettlement(personId, settlementId)
                        )
                    }
                )
            }

            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    onAddExpense = { navController.navigate(Routes.ADD_EXPENSE) },
                    onEditExpense = { id -> navController.navigate(Routes.editExpense(id)) }
                )
            }

            composable(Routes.FORECAST) {
                ForecastScreen(
                    onOpenCalendar = { navController.navigate(Routes.CALENDAR) },
                    onOpenAffordability = { navController.navigate(Routes.AFFORDABILITY) }
                )
            }

            composable(Routes.PEOPLE) {
                PeopleScreen(
                    onBack = { navController.popBackStack() },
                    onAddPerson = { navController.navigate(Routes.ADD_PERSON) },
                    onOpenPerson = { id -> navController.navigate(Routes.personDetail(id)) },
                    onAddSharedExpense = { navController.navigate(Routes.ADD_SHARED_EXPENSE) }
                )
            }

            composable(Routes.MORE) {
                MoreScreen(
                    onOpenRecentActivity = { navController.navigate(Routes.RECENT_ACTIVITY) },
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenPeople = { navController.navigate(Routes.PEOPLE) },
                    onOpenIncome = { navController.navigate(Routes.INCOME) },
                    onOpenEmis = { navController.navigate(Routes.EMIS) },
                    onOpenBills = { navController.navigate(Routes.BILLS) },
                    onOpenCards = { navController.navigate(Routes.CARDS) },
                    onOpenAnnual = { navController.navigate(Routes.ANNUAL) },
                    onOpenGoals = { navController.navigate(Routes.GOALS) },
                    onOpenEmergencyFund = { navController.navigate(Routes.EMERGENCY_FUND) },
                    onOpenVehicles = { navController.navigate(Routes.VEHICLES) },
                    onOpenFamily = { navController.navigate(Routes.FAMILY) },
                    onOpenCalendar = { navController.navigate(Routes.CALENDAR) },
                    onOpenReports = { navController.navigate(Routes.REPORTS) },
                    onOpenAffordability = { navController.navigate(Routes.AFFORDABILITY) },
                    onOpenAssistant = { navController.navigate(Routes.ASSISTANT) },
                    onOpenBudgets = { navController.navigate(Routes.BUDGETS) },
                    onOpenSmsImport = { navController.navigate(Routes.SMS_IMPORT) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            // ---- Expenses --------------------------------------------------------

            composable(Routes.ADD_EXPENSE) {
                ExpenseEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.EDIT_EXPENSE,
                arguments = listOf(navArgument(Routes.ARG_EXPENSE_ID) { type = NavType.StringType })
            ) {
                ExpenseEditorScreen(onBack = { navController.popBackStack() })
            }

            // ---- Income ----------------------------------------------------------

            composable(Routes.INCOME) {
                IncomeScreen(
                    onBack = { navController.popBackStack() },
                    onAddSource = { navController.navigate(Routes.ADD_INCOME_SOURCE) },
                    onEditSource = { id -> navController.navigate(Routes.editIncomeSource(id)) },
                    onAddReceipt = { navController.navigate(Routes.ADD_INCOME_RECEIPT) },
                    onEditReceipt = { id ->
                        navController.navigate(Routes.editIncomeReceipt(id))
                    }
                )
            }

            composable(Routes.ADD_INCOME_SOURCE) {
                IncomeSourceEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.EDIT_INCOME_SOURCE,
                arguments = listOf(navArgument(Routes.ARG_SOURCE_ID) { type = NavType.StringType })
            ) {
                IncomeSourceEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.ADD_INCOME_RECEIPT) {
                IncomeReceiptScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.EDIT_INCOME_RECEIPT,
                arguments = listOf(navArgument(Routes.ARG_RECEIPT_ID) { type = NavType.StringType })
            ) {
                IncomeReceiptScreen(onBack = { navController.popBackStack() })
            }

            // ---- People ----------------------------------------------------------

            composable(Routes.ADD_PERSON) {
                AddPersonScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.popBackStack()
                        navController.navigate(Routes.personDetail(id))
                    }
                )
            }

            composable(
                route = Routes.PERSON_DETAIL,
                arguments = listOf(
                    navArgument(Routes.ARG_PERSON_ID) { type = NavType.StringType },
                    // Optional: set when arriving from the activity feed to correct one
                    // settlement, so the sheet opens on it directly.
                    navArgument(Routes.ARG_SETTLEMENT_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val personId = entry.arguments?.getString(Routes.ARG_PERSON_ID)?.toLongOrNull() ?: 0L
                PersonDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAddObligation = { navController.navigate(Routes.addObligation(personId)) }
                )
            }

            composable(
                route = Routes.ADD_OBLIGATION,
                arguments = listOf(navArgument(Routes.ARG_PERSON_ID) { type = NavType.StringType })
            ) {
                ObligationEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.ADD_SHARED_EXPENSE) {
                SharedExpenseScreen(
                    onBack = { navController.popBackStack() },
                    onAddPerson = { navController.navigate(Routes.ADD_PERSON) }
                )
            }

            // ---- EMIs ------------------------------------------------------------

            composable(Routes.PLANS) {
                EmiListScreen(
                    onBack = null,
                    onAddEmi = { navController.navigate(Routes.ADD_EMI) },
                    onOpenEmi = { id -> navController.navigate(Routes.emiDetail(id)) },
                    onOpenBills = { navController.navigate(Routes.BILLS) },
                    onOpenCards = { navController.navigate(Routes.CARDS) },
                    onOpenAnnual = { navController.navigate(Routes.ANNUAL) }
                )
            }

            composable(Routes.EMIS) {
                EmiListScreen(
                    onBack = { navController.popBackStack() },
                    onAddEmi = { navController.navigate(Routes.ADD_EMI) },
                    onOpenEmi = { id -> navController.navigate(Routes.emiDetail(id)) },
                    onOpenBills = { navController.navigate(Routes.BILLS) },
                    onOpenCards = { navController.navigate(Routes.CARDS) },
                    onOpenAnnual = { navController.navigate(Routes.ANNUAL) }
                )
            }

            composable(Routes.ADD_EMI) {
                EmiEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.EMI_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_EMI_ID) { type = NavType.StringType })
            ) {
                EmiDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Routes.editEmi(id)) }
                )
            }

            composable(
                route = Routes.EDIT_EMI,
                arguments = listOf(navArgument(Routes.ARG_EMI_ID) { type = NavType.StringType })
            ) {
                EmiEditorScreen(onBack = { navController.popBackStack() })
            }

            // ---- Cards, bills, yearly expenses -----------------------------------

            composable(Routes.CARDS) {
                CardsScreen(
                    onBack = { navController.popBackStack() },
                    onAddCard = { navController.navigate(Routes.ADD_CARD) },
                    onEditCard = { id -> navController.navigate(Routes.editCard(id)) }
                )
            }

            composable(Routes.ADD_CARD) {
                CardEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.EDIT_CARD,
                arguments = listOf(navArgument(Routes.ARG_CARD_ID) { type = NavType.StringType })
            ) {
                CardEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.BILLS) {
                BillsScreen(
                    onBack = { navController.popBackStack() },
                    onAddBill = { navController.navigate(Routes.ADD_BILL) },
                    onEditBill = { id -> navController.navigate(Routes.editBill(id)) }
                )
            }

            composable(Routes.ADD_BILL) {
                BillEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.EDIT_BILL,
                arguments = listOf(navArgument(Routes.ARG_BILL_ID) { type = NavType.StringType })
            ) {
                BillEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.ANNUAL) {
                AnnualScreen(
                    onBack = { navController.popBackStack() },
                    onAddAnnual = { navController.navigate(Routes.ADD_ANNUAL) },
                    onEditAnnual = { id -> navController.navigate(Routes.editAnnual(id)) }
                )
            }

            composable(Routes.ADD_ANNUAL) {
                AnnualEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.EDIT_ANNUAL,
                arguments = listOf(navArgument(Routes.ARG_ANNUAL_ID) { type = NavType.StringType })
            ) {
                AnnualEditorScreen(onBack = { navController.popBackStack() })
            }

            // ---- Saving ----------------------------------------------------------

            composable(Routes.GOALS) {
                GoalsScreen(
                    onBack = null,
                    onAddGoal = { navController.navigate(Routes.ADD_GOAL) },
                    onOpenGoal = { id -> navController.navigate(Routes.goalDetail(id)) }
                )
            }

            composable(Routes.ADD_GOAL) {
                GoalEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.GOAL_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_GOAL_ID) { type = NavType.StringType })
            ) {
                GoalDetailScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.EMERGENCY_FUND) {
                EmergencyFundScreen(onBack = { navController.popBackStack() })
            }

            // ---- Household -------------------------------------------------------

            composable(Routes.VEHICLES) {
                VehiclesScreen(
                    onBack = { navController.popBackStack() },
                    onAddVehicle = { navController.navigate(Routes.ADD_VEHICLE) }
                )
            }

            composable(Routes.ADD_VEHICLE) {
                VehicleEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.FAMILY) {
                FamilyScreen(onBack = { navController.popBackStack() })
            }

            // ---- Understand ------------------------------------------------------

            composable(Routes.CALENDAR) {
                CalendarScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.REPORTS) {
                ReportsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SMS_IMPORT) {
                SmsImportScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.BUDGETS) {
                BudgetsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.ASSISTANT) {
                AssistantScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { action ->
                        val route = when (action) {
                            AssistantAction.OPEN_DASHBOARD -> Routes.DASHBOARD
                            AssistantAction.OPEN_FORECAST -> Routes.FORECAST
                            AssistantAction.OPEN_PEOPLE -> Routes.PEOPLE
                            AssistantAction.OPEN_PLANS -> Routes.PLANS
                            AssistantAction.OPEN_CALENDAR -> Routes.CALENDAR
                            AssistantAction.OPEN_GOALS -> Routes.GOALS
                            AssistantAction.OPEN_EMERGENCY_FUND -> Routes.EMERGENCY_FUND
                            AssistantAction.OPEN_REPORTS -> Routes.REPORTS
                            AssistantAction.OPEN_INCOME -> Routes.INCOME
                            AssistantAction.OPEN_AFFORDABILITY -> Routes.AFFORDABILITY
                            AssistantAction.NONE -> null
                        }
                        route?.let { navController.navigate(it) }
                    }
                )
            }

            composable(Routes.AFFORDABILITY) {
                AffordabilityScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenExpense = { id -> navController.navigate(Routes.editExpense(id)) },
                    onOpenPerson = { id -> navController.navigate(Routes.personDetail(id)) },
                    onOpenEmi = { id -> navController.navigate(Routes.emiDetail(id)) },
                    onOpenBills = { navController.navigate(Routes.BILLS) }
                )
            }

            // ---- Settings --------------------------------------------------------

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) }
                )
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.ACCOUNTS) {
                AccountsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
