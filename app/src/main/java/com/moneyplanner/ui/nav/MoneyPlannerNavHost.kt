package com.moneyplanner.ui.nav

import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
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
import dagger.hilt.android.EntryPointAccessors

/** Every route on the bottom bar. A screen outside this set is reached from one of them. */
private val TOP_LEVEL_ROUTES: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()

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
    val showBars = currentRoute in TOP_LEVEL_ROUTES

    val context = LocalContext.current
    val tabFocus = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            TabFocusEntryPoint::class.java
        ).tabFocus()
    }

    Scaffold(
        bottomBar = {
            if (showBars) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == destination.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination.route) },
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
            // Consumed as well as applied. The padding already keeps every screen clear of
            // the status and navigation bars, and without saying so a screen's own top bar
            // added the status bar a second time, leaving an empty band above every title
            // and lists stopping a bar's height short of the bottom.
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            // ---- Bottom bar destinations ---------------------------------------

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onAddExpense = { navController.navigate(Routes.ADD_EXPENSE) },
                    onAddIncome = { navController.navigate(Routes.ADD_INCOME_RECEIPT) },
                    onOpenEmis = { navController.navigate(Routes.PLANS) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    // Every link from Home is about now, so each tab is asked to show the
                    // month in progress rather than whichever month it was last left on.
                    onOpenForecast = {
                        tabFocus.forecast.request(Unit)
                        navController.navigateToTopLevel(Routes.FORECAST)
                    },
                    onOpenPeople = { navController.navigate(Routes.PEOPLE) },
                    onOpenCards = { navController.navigate(Routes.CARDS) },
                    onOpenGoals = { navController.navigate(Routes.GOALS) },
                    onOpenEmergencyFund = { navController.navigate(Routes.EMERGENCY_FUND) },
                    onOpenCalendar = {
                        tabFocus.calendar.request(null)
                        navController.navigateToTopLevel(Routes.CALENDAR)
                    },
                    onOpenTransactions = { navController.navigate(Routes.transactions()) },
                    onOpenUnassigned = {
                        navController.navigate(Routes.unassignedTransactions())
                    },
                    onOpenAffordability = { navController.navigate(Routes.AFFORDABILITY) },
                    onOpenAssistant = { navController.navigate(Routes.ASSISTANT) },
                    onOpenBudgets = { navController.navigate(Routes.BUDGETS) }
                )
            }

            composable(Routes.FORECAST) {
                ForecastScreen(
                    // The month the link is about, which for a shortfall is usually months
                    // ahead of the one the calendar would otherwise open on.
                    onOpenCalendar = { month ->
                        tabFocus.calendar.request(month)
                        navController.navigateToTopLevel(Routes.CALENDAR)
                    },
                    onOpenAffordability = { navController.navigate(Routes.AFFORDABILITY) }
                )
            }

            composable(Routes.MORE) {
                MoreScreen(
                    onOpenTransactions = { navController.navigate(Routes.transactions()) },
                    onOpenRecentActivity = { navController.navigate(Routes.RECENT_ACTIVITY) },
                    onOpenAccounts = { navController.navigate(Routes.accounts()) },
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
                    onOpenAffordability = { navController.navigate(Routes.AFFORDABILITY) },
                    onOpenAssistant = { navController.navigate(Routes.ASSISTANT) },
                    onOpenBudgets = { navController.navigate(Routes.BUDGETS) },
                    onOpenSmsImport = { navController.navigate(Routes.SMS_IMPORT) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            // ---- History ---------------------------------------------------------

            // Neither is on the bar, so each takes a back handler. The bar is hidden on
            // every route that is not a tab, and without a back arrow nothing on screen
            // would lead out again.
            composable(
                route = Routes.TRANSACTIONS,
                arguments = listOf(
                    // Optional: set by the dashboard's "expenses with no account" row, which
                    // opens every such expense instead of one month of everything.
                    navArgument(Routes.ARG_UNASSIGNED) {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) {
                TransactionsScreen(
                    onBack = { navController.popBackStackSafely() },
                    onAddExpense = { navController.navigate(Routes.ADD_EXPENSE) },
                    onEditExpense = { id -> navController.navigate(Routes.editExpense(id)) }
                )
            }

            composable(Routes.RECENT_ACTIVITY) {
                RecentActivityScreen(
                    onBack = { navController.popBackStackSafely() },
                    onEditExpense = { id -> navController.navigate(Routes.editExpense(id)) },
                    onEditIncome = { id ->
                        navController.navigate(Routes.editIncomeReceipt(id))
                    },
                    onEditSettlement = { personId, settlementId ->
                        navController.navigate(
                            Routes.personDetailEditingSettlement(personId, settlementId)
                        )
                    },
                    onEditTransfer = { id ->
                        navController.navigate(Routes.accountsEditingTransfer(id))
                    },
                    onEditContribution = { goalId, contributionId ->
                        navController.navigate(
                            Routes.goalDetailEditingContribution(goalId, contributionId)
                        )
                    }
                )
            }

            // ---- Expenses --------------------------------------------------------

            composable(Routes.ADD_EXPENSE) {
                ExpenseEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(
                route = Routes.EDIT_EXPENSE,
                arguments = listOf(navArgument(Routes.ARG_EXPENSE_ID) { type = NavType.StringType })
            ) {
                ExpenseEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            // ---- Income ----------------------------------------------------------

            composable(Routes.INCOME) {
                IncomeScreen(
                    onBack = { navController.popBackStackSafely() },
                    onAddSource = { navController.navigate(Routes.ADD_INCOME_SOURCE) },
                    onEditSource = { id -> navController.navigate(Routes.editIncomeSource(id)) },
                    onAddReceipt = { navController.navigate(Routes.ADD_INCOME_RECEIPT) },
                    onEditReceipt = { id ->
                        navController.navigate(Routes.editIncomeReceipt(id))
                    }
                )
            }

            composable(Routes.ADD_INCOME_SOURCE) {
                IncomeSourceEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(
                route = Routes.EDIT_INCOME_SOURCE,
                arguments = listOf(navArgument(Routes.ARG_SOURCE_ID) { type = NavType.StringType })
            ) {
                IncomeSourceEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(Routes.ADD_INCOME_RECEIPT) {
                IncomeReceiptScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(
                route = Routes.EDIT_INCOME_RECEIPT,
                arguments = listOf(navArgument(Routes.ARG_RECEIPT_ID) { type = NavType.StringType })
            ) {
                IncomeReceiptScreen(onBack = { navController.popBackStackSafely() })
            }

            // ---- People ----------------------------------------------------------

            composable(Routes.PEOPLE) {
                PeopleScreen(
                    onBack = { navController.popBackStackSafely() },
                    onAddPerson = { navController.navigate(Routes.ADD_PERSON) },
                    onOpenPerson = { id -> navController.navigate(Routes.personDetail(id)) },
                    onAddSharedExpense = { navController.navigate(Routes.ADD_SHARED_EXPENSE) }
                )
            }

            composable(Routes.ADD_PERSON) {
                AddPersonScreen(
                    onBack = { navController.popBackStackSafely() },
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
                    onBack = { navController.popBackStackSafely() },
                    onAddObligation = { navController.navigate(Routes.addObligation(personId)) }
                )
            }

            composable(
                route = Routes.ADD_OBLIGATION,
                arguments = listOf(navArgument(Routes.ARG_PERSON_ID) { type = NavType.StringType })
            ) {
                ObligationEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(Routes.ADD_SHARED_EXPENSE) {
                SharedExpenseScreen(
                    onBack = { navController.popBackStackSafely() },
                    onAddPerson = { navController.navigate(Routes.ADD_PERSON) }
                )
            }

            // ---- EMIs ------------------------------------------------------------

            // One screen behind two routes. Plans is the hub Home and the assistant open,
            // introduced as everything committed to; EMIs is the loan list More opens.
            // Neither is a tab, so both take a back handler.
            composable(Routes.PLANS) {
                EmiListScreen(
                    onBack = { navController.popBackStackSafely() },
                    isPlansHub = true,
                    onAddEmi = { navController.navigate(Routes.ADD_EMI) },
                    onOpenEmi = { id -> navController.navigate(Routes.emiDetail(id)) },
                    onOpenBills = { navController.navigate(Routes.BILLS) },
                    onOpenCards = { navController.navigate(Routes.CARDS) },
                    onOpenAnnual = { navController.navigate(Routes.ANNUAL) }
                )
            }

            composable(Routes.EMIS) {
                EmiListScreen(
                    onBack = { navController.popBackStackSafely() },
                    isPlansHub = false,
                    onAddEmi = { navController.navigate(Routes.ADD_EMI) },
                    onOpenEmi = { id -> navController.navigate(Routes.emiDetail(id)) },
                    onOpenBills = { navController.navigate(Routes.BILLS) },
                    onOpenCards = { navController.navigate(Routes.CARDS) },
                    onOpenAnnual = { navController.navigate(Routes.ANNUAL) }
                )
            }

            composable(Routes.ADD_EMI) {
                EmiEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(
                route = Routes.EMI_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_EMI_ID) { type = NavType.StringType })
            ) {
                EmiDetailScreen(
                    onBack = { navController.popBackStackSafely() },
                    onEdit = { id -> navController.navigate(Routes.editEmi(id)) }
                )
            }

            composable(
                route = Routes.EDIT_EMI,
                arguments = listOf(navArgument(Routes.ARG_EMI_ID) { type = NavType.StringType })
            ) {
                EmiEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            // ---- Cards, bills, yearly expenses -----------------------------------

            composable(Routes.CARDS) {
                CardsScreen(
                    onBack = { navController.popBackStackSafely() },
                    onAddCard = { navController.navigate(Routes.ADD_CARD) },
                    onEditCard = { id -> navController.navigate(Routes.editCard(id)) }
                )
            }

            composable(Routes.ADD_CARD) {
                CardEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(
                route = Routes.EDIT_CARD,
                arguments = listOf(navArgument(Routes.ARG_CARD_ID) { type = NavType.StringType })
            ) {
                CardEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(Routes.BILLS) {
                BillsScreen(
                    onBack = { navController.popBackStackSafely() },
                    onAddBill = { navController.navigate(Routes.ADD_BILL) },
                    onEditBill = { id -> navController.navigate(Routes.editBill(id)) }
                )
            }

            composable(Routes.ADD_BILL) {
                BillEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(
                route = Routes.EDIT_BILL,
                arguments = listOf(navArgument(Routes.ARG_BILL_ID) { type = NavType.StringType })
            ) {
                BillEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(Routes.ANNUAL) {
                AnnualScreen(
                    onBack = { navController.popBackStackSafely() },
                    onAddAnnual = { navController.navigate(Routes.ADD_ANNUAL) },
                    onEditAnnual = { id -> navController.navigate(Routes.editAnnual(id)) }
                )
            }

            composable(Routes.ADD_ANNUAL) {
                AnnualEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(
                route = Routes.EDIT_ANNUAL,
                arguments = listOf(navArgument(Routes.ARG_ANNUAL_ID) { type = NavType.StringType })
            ) {
                AnnualEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            // ---- Saving ----------------------------------------------------------

            composable(Routes.GOALS) {
                GoalsScreen(
                    onBack = { navController.popBackStackSafely() },
                    onAddGoal = { navController.navigate(Routes.ADD_GOAL) },
                    onOpenGoal = { id -> navController.navigate(Routes.goalDetail(id)) }
                )
            }

            composable(Routes.ADD_GOAL) {
                GoalEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(
                route = Routes.GOAL_DETAIL,
                arguments = listOf(
                    navArgument(Routes.ARG_GOAL_ID) { type = NavType.StringType },
                    // Optional: set when arriving from the activity feed to correct one
                    // entry, so the sheet opens on it directly.
                    navArgument(Routes.ARG_CONTRIBUTION_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                GoalDetailScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(Routes.EMERGENCY_FUND) {
                EmergencyFundScreen(onBack = { navController.popBackStackSafely() })
            }

            // ---- Household -------------------------------------------------------

            composable(Routes.VEHICLES) {
                VehiclesScreen(
                    onBack = { navController.popBackStackSafely() },
                    onAddVehicle = { navController.navigate(Routes.ADD_VEHICLE) }
                )
            }

            composable(Routes.ADD_VEHICLE) {
                VehicleEditorScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(Routes.FAMILY) {
                FamilyScreen(onBack = { navController.popBackStackSafely() })
            }

            // ---- Understand ------------------------------------------------------

            // Both are bottom-bar destinations now, so neither takes a back handler:
            // there is nothing behind a tab to go back to.
            composable(Routes.CALENDAR) {
                CalendarScreen(onBack = null)
            }

            composable(Routes.REPORTS) {
                ReportsScreen(onBack = null)
            }

            composable(Routes.SMS_IMPORT) {
                SmsImportScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(Routes.BUDGETS) {
                BudgetsScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(Routes.ASSISTANT) {
                AssistantScreen(
                    onBack = { navController.popBackStackSafely() },
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
                        // An answer is about the present, so a tab it opens shows the month
                        // in progress, not the one the user last browsed to there.
                        when (action) {
                            AssistantAction.OPEN_CALENDAR -> tabFocus.calendar.request(null)
                            AssistantAction.OPEN_FORECAST -> tabFocus.forecast.request(Unit)
                            AssistantAction.OPEN_REPORTS -> tabFocus.reports.request(null)
                            else -> Unit
                        }
                        route?.let {
                            if (it in TOP_LEVEL_ROUTES) {
                                navController.navigateToTopLevel(it)
                            } else {
                                navController.navigate(it)
                            }
                        }
                    }
                )
            }

            composable(Routes.AFFORDABILITY) {
                AffordabilityScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStackSafely() },
                    onOpenExpense = { id -> navController.navigate(Routes.editExpense(id)) },
                    onOpenPerson = { id -> navController.navigate(Routes.personDetail(id)) },
                    onOpenEmi = { id -> navController.navigate(Routes.emiDetail(id)) },
                    onOpenBills = { navController.navigate(Routes.BILLS) }
                )
            }

            // ---- Settings --------------------------------------------------------

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStackSafely() },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenAccounts = { navController.navigate(Routes.accounts()) }
                )
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(
                route = Routes.ACCOUNTS,
                arguments = listOf(
                    // Optional: set when arriving from the activity feed to correct one
                    // transfer, so the sheet opens on it directly.
                    navArgument(Routes.ARG_TRANSFER_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                AccountsScreen(onBack = { navController.popBackStackSafely() })
            }
        }
    }
}

/**
 * Opens a bottom-bar destination exactly as tapping it on the bar would.
 *
 * A plain navigate() pushes another copy of the tab on top of whatever is showing, so Back
 * walks through duplicate tabs and the bar's saved state for that tab is bypassed. Popping
 * to the start destination while saving what was there keeps a single copy of each tab,
 * and restoring brings back what the user was last looking at on it. Every link into a tab
 * goes through here, not only the bar, so the back stack has the same shape however the
 * user arrived.
 *
 * A screen that is not a tab, such as the assistant, is closed first rather than saved.
 * Saving it would file it as tab state: when the start destination has nothing recorded
 * yet, Navigation maps a non-inclusive pop's saved screens to it, so "Open dashboard" from
 * the assistant restored the assistant on the spot, and a later tap on Home or More would
 * have brought it back with the bar hidden. Only tab roots are ever saved this way.
 */
private fun NavHostController.navigateToTopLevel(route: String) {
    while (currentDestination?.route !in TOP_LEVEL_ROUTES && previousBackStackEntry != null) {
        if (!popBackStack()) break
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Leaves the current screen, when it is the one actually showing.
 *
 * A screen stays on screen while it animates away, so a quick second tap on its back arrow
 * still reaches it and pops whatever is underneath as well. On a screen opened straight
 * from Home that pops the start destination itself, leaving an empty window with no bar
 * and nothing to tap. The entry being left is only resumed once its arrival has finished,
 * so a tap during either animation is ignored, and the root is never popped at all.
 */
private fun NavHostController.popBackStackSafely() {
    val showing = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED
    if (showing && previousBackStackEntry != null) popBackStack()
}
