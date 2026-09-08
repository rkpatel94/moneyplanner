package com.moneyplanner.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every destination in the app.
 *
 * Routes are plain strings with typed helpers for the ones that take an id, so a screen
 * cannot be navigated to with an argument in the wrong position.
 */
object Routes {

    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "transactions"
    const val FORECAST = "forecast"
    const val PEOPLE = "people"
    const val PLANS = "plans"
    const val MORE = "more"

    const val ADD_EXPENSE = "expense/add"
    const val EDIT_EXPENSE = "expense/edit/{expenseId}"
    fun editExpense(id: Long) = "expense/edit/$id"

    const val INCOME = "income"
    const val ADD_INCOME_SOURCE = "income/source/add"
    const val EDIT_INCOME_SOURCE = "income/source/edit/{sourceId}"
    fun editIncomeSource(id: Long) = "income/source/edit/$id"
    const val ADD_INCOME_RECEIPT = "income/receipt/add"
    const val EDIT_INCOME_RECEIPT = "income/receipt/edit/{receiptId}"
    fun editIncomeReceipt(id: Long) = "income/receipt/edit/$id"

    const val ADD_PERSON = "people/add"
    const val PERSON_DETAIL = "people/{personId}?editSettlement={settlementId}"
    fun personDetail(id: Long) = "people/$id"

    /** Opens a person and puts one of their settlements straight into the edit sheet. */
    fun personDetailEditingSettlement(personId: Long, settlementId: Long) =
        "people/$personId?editSettlement=$settlementId"
    const val ADD_OBLIGATION = "people/{personId}/obligation"
    fun addObligation(id: Long) = "people/$id/obligation"
    const val ADD_SHARED_EXPENSE = "people/shared/add"

    const val EMIS = "emis"
    const val ADD_EMI = "emis/add"
    const val EMI_DETAIL = "emis/{emiId}"
    fun emiDetail(id: Long) = "emis/$id"
    const val EDIT_EMI = "emis/edit/{emiId}"
    fun editEmi(id: Long) = "emis/edit/$id"

    const val CARDS = "cards"
    const val ADD_CARD = "cards/add"
    const val EDIT_CARD = "cards/edit/{cardId}"
    fun editCard(id: Long) = "cards/edit/$id"

    const val BILLS = "bills"
    const val ADD_BILL = "bills/add"
    const val EDIT_BILL = "bills/edit/{billId}"
    fun editBill(id: Long) = "bills/edit/$id"

    const val ANNUAL = "annual"
    const val ADD_ANNUAL = "annual/add"
    const val EDIT_ANNUAL = "annual/edit/{annualId}"
    fun editAnnual(id: Long) = "annual/edit/$id"

    const val GOALS = "goals"
    const val ADD_GOAL = "goals/add"
    const val GOAL_DETAIL = "goals/{goalId}?editContribution={contributionId}"
    fun goalDetail(id: Long) = "goals/$id"

    /** Opens a goal with one of its entries already in the edit sheet. */
    fun goalDetailEditingContribution(goalId: Long, contributionId: Long) =
        "goals/$goalId?editContribution=$contributionId"

    const val EMERGENCY_FUND = "emergency"
    const val VEHICLES = "vehicles"
    const val ADD_VEHICLE = "vehicles/add"

    const val FAMILY = "family"
    const val CALENDAR = "calendar"
    const val REPORTS = "reports"
    const val AFFORDABILITY = "afford"
    const val ASSISTANT = "assistant"
    const val BUDGETS = "budgets"
    const val SMS_IMPORT = "sms-import"
    const val SEARCH = "search"
    const val RECENT_ACTIVITY = "activity"
    // Settings is one scrolling screen rather than a hub, so notifications, security and
    // backup are sections within it and have no routes of their own.
    const val SETTINGS = "settings"
    const val CATEGORIES = "settings/categories"
    const val ACCOUNTS = "settings/accounts?editTransfer={transferId}"
    fun accounts() = "settings/accounts"

    /** Opens Accounts with one transfer already in the edit sheet. */
    fun accountsEditingTransfer(transferId: Long) =
        "settings/accounts?editTransfer=$transferId"

    const val ARG_EXPENSE_ID = "expenseId"
    const val ARG_SOURCE_ID = "sourceId"
    const val ARG_RECEIPT_ID = "receiptId"
    const val ARG_PERSON_ID = "personId"
    const val ARG_SETTLEMENT_ID = "settlementId"
    const val ARG_TRANSFER_ID = "transferId"
    const val ARG_EMI_ID = "emiId"
    const val ARG_CARD_ID = "cardId"
    const val ARG_BILL_ID = "billId"
    const val ARG_ANNUAL_ID = "annualId"
    const val ARG_GOAL_ID = "goalId"
    const val ARG_CONTRIBUTION_ID = "contributionId"
}

/**
 * The five destinations on the bottom bar, as specified by the design.
 *
 * People and shared expenses sit inside More rather than on the bar. The bar is reserved
 * for the things a person opens most days, and a bar that holds everything stops being
 * navigable at a glance.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME(Routes.DASHBOARD, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    HISTORY(
        Routes.TRANSACTIONS,
        "History",
        Icons.AutoMirrored.Outlined.ReceiptLong,
        Icons.AutoMirrored.Filled.ReceiptLong
    ),
    PLANS(Routes.PLANS, "Plans", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    GOALS(Routes.GOALS, "Goals", Icons.Outlined.TrackChanges, Icons.Filled.TrackChanges),
    MORE(Routes.MORE, "More", Icons.Outlined.Menu, Icons.Filled.Menu)
}
