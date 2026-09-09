package com.moneyplanner.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.CalendarCalculator
import com.moneyplanner.domain.calc.CalendarEntry
import com.moneyplanner.domain.calc.ForecastItem
import com.moneyplanner.ui.components.LoadingState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.screens.expenses.MonthSelector
import com.moneyplanner.ui.theme.MoneyTheme
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    /**
     * Null until the user picks one, so the month shown follows the snapshot's date.
     *
     * Reading the system clock here would fix the calendar to whichever month it was
     * opened in and leave it there across midnight, which is exactly what the snapshot
     * already solves for every other screen.
     */
    private val selectedMonth = MutableStateFlow<YearMonth?>(null)

    val state: StateFlow<CalendarState> = combine(
        snapshotRepository.snapshot,
        selectedMonth
    ) { snapshot, chosen ->
        val month = chosen ?: snapshot.currentMonth

        CalendarState(
            month = month,
            entriesByDate = CalendarCalculator.byDate(snapshot, month),
            today = snapshot.today,
            isPastMonth = month.isBefore(snapshot.currentMonth),
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarState()
    )

    fun previousMonth() = selectedMonth.update { (it ?: state.value.month).minusMonths(1) }
    fun nextMonth() = selectedMonth.update { (it ?: state.value.month).plusMonths(1) }
}

data class CalendarState(
    val month: YearMonth = YearMonth.now(),
    val entriesByDate: Map<LocalDate, List<CalendarEntry>> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val isPastMonth: Boolean = false,
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean get() = entriesByDate.isEmpty()
}

/**
 * A month at a glance.
 *
 * Days carry a small marker for money in and money out, so the shape of the month is
 * readable before any number is. Selecting a day lists exactly what falls on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val colors = MoneyTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Money calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MonthSelector(
                    label = DateUtil.formatMonth(state.month),
                    onPrevious = {
                        selectedDate = null
                        viewModel.previousMonth()
                    },
                    onNext = {
                        selectedDate = null
                        viewModel.nextMonth()
                    }
                )
            }

            item {
                SectionCard {
                    WeekdayHeader()
                    Spacer(Modifier.height(6.dp))
                    MonthGrid(
                        month = state.month,
                        today = state.today,
                        selected = selectedDate,
                        entriesByDate = state.entriesByDate,
                        onSelect = { date ->
                            selectedDate = if (selectedDate == date) null else date
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot("Money in", colors.positive)
                        LegendDot("Money out", colors.negative)
                        LegendDot("Overdue", colors.warning)
                    }
                }
            }

            val chosen = selectedDate
            val entriesForDay = chosen?.let { state.entriesByDate[it] }.orEmpty()

            if (chosen != null) {
                item {
                    SectionCard(title = DateUtil.formatWeekdayDate(chosen)) {
                        if (entriesForDay.isEmpty()) {
                            Text(
                                "Nothing on this day.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            entriesForDay.forEach { entry -> CalendarEntryRow(entry) }
                        }
                    }
                }
            } else if (!state.isEmpty) {
                item {
                    SectionCard(title = "Everything this month") {
                        state.entriesByDate.keys.sorted().forEach { date ->
                            Text(
                                DateUtil.formatWeekdayDate(date),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                            state.entriesByDate.getValue(date).forEach { entry ->
                                CalendarEntryRow(entry)
                            }
                        }
                    }
                }
            } else {
                item {
                    SectionCard {
                        Text(
                            // A finished month can only ever hold what happened, so
                            // saying nothing is "scheduled" in it would be answering a
                            // question the user did not ask.
                            if (state.isPastMonth) {
                                "Nothing was recorded in " +
                                    DateUtil.formatMonth(state.month) + "."
                            } else {
                                "Nothing is scheduled in " +
                                    DateUtil.formatMonth(state.month) + "."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    entriesByDate: Map<LocalDate, List<CalendarEntry>>,
    onSelect: (LocalDate) -> Unit
) {
    val firstDay = month.atDay(1)
    // Monday-first grid, matching how Indian calendars are usually printed.
    val leadingBlanks = (firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val totalCells = leadingBlanks + month.lengthOfMonth()
    val rows = (totalCells + 6) / 7

    Column {
        repeat(rows) { rowIndex ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { columnIndex ->
                    val cellIndex = rowIndex * 7 + columnIndex
                    val dayNumber = cellIndex - leadingBlanks + 1

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNumber in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selected,
                                entries = entriesByDate[date].orEmpty(),
                                onClick = { onSelect(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    entries: List<CalendarEntry>,
    onClick: () -> Unit
) {
    val colors = MoneyTheme.colors
    val hasInflow = entries.any { it.isInflow }
    // Neutral movements carry no dot: a transfer changes no total, so marking it as
    // money out would say something about the month that is not true.
    val hasOutflow = entries.any { it.isOutflow }
    val hasOverdue = entries.any { it.isOverdue }

    val background = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }

    // Read aloud, so it has to say what a sighted user gets from the dots: whether the
    // day holds anything, and whether any of it is late.
    val description = buildString {
        append(DateUtil.formatDate(date))
        if (entries.isEmpty()) {
            append(", nothing")
        } else {
            append(", ${entries.size} ")
            append(if (entries.size == 1) "entry" else "entries")
            if (hasOverdue) append(", something is overdue")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(10.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Three independent markers, matching the legend exactly. Overdue is its
                // own dot rather than a recolouring of the outflow one, because a salary
                // that has not arrived is overdue too and would otherwise stay green.
                if (hasInflow) Dot(colors.positive)
                if (hasOutflow) Dot(colors.negative)
                if (hasOverdue) Dot(colors.warning)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalendarEntryRow(entry: CalendarEntry) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // A payment that has left the account and one that is merely due look the
            // same on a grid, and treating them alike is how somebody concludes a bill
            // is paid when it is not.
            if (entry.isOverdue) {
                Spacer(Modifier.height(4.dp))
                StatusPill(
                    text = "Was due, still not recorded",
                    containerColor = colors.warningContainer,
                    contentColor = colors.onWarningContainer
                )
            } else if (entry.isScheduled) {
                Spacer(Modifier.height(4.dp))
                StatusPill(text = "Expected")
            }
        }
        MoneyText(
            money = entry.amount,
            color = when {
                entry.isInflow -> colors.positive
                entry.isOutflow -> colors.negative
                else -> colors.neutral
            }
        )
    }
}
