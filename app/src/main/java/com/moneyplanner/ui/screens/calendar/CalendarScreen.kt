package com.moneyplanner.ui.screens.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.moneyplanner.ui.components.dashedOutline
import com.moneyplanner.ui.theme.PillLabelStyle
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.moneyplanner.ui.components.Confidence
import com.moneyplanner.ui.components.ConfidencePill
import com.moneyplanner.ui.components.MonthSelector
import com.moneyplanner.ui.components.LoadingState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.theme.MoneyTheme
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import com.moneyplanner.ui.nav.TabFocus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
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
    tabFocus: TabFocus,
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

    init {
        // A link names the month it is about; null hands the choice back to the snapshot's
        // date. The bar alone returns to whichever month was last paged to here.
        viewModelScope.launch {
            tabFocus.calendar.requests.collect { selectedMonth.value = it }
        }
    }

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

    /** Back to following the snapshot's date, so the month shown moves on at midnight again. */
    fun showCurrentMonth() {
        selectedMonth.value = null
    }
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
 * The past is solid and the future is light: days already lived carry solid dots for money
 * that moved, days still to come sit on a pale well, and a day with something projected on
 * it has a dashed lavender outline. Today is the indigo square with the marigold ring, the
 * horizon between the two. Today is chosen until another day is tapped, so the screen opens
 * on what matters now.
 */
@Composable
fun CalendarScreen(
    onBack: (() -> Unit)?,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picked by remember(state.month) { mutableStateOf<LocalDate?>(null) }
    var filter by remember { mutableStateOf(EntryFilter.ALL) }
    val currentMonth = YearMonth.from(state.today)
    val selected = picked ?: state.today.takeIf { state.month == currentMonth }

    if (state.isLoading) {
        LoadingState()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { CalendarHeader(onBack) }

        item {
            MonthSelector(
                month = state.month,
                currentMonth = currentMonth,
                onPrevious = {
                    picked = null
                    viewModel.previousMonth()
                },
                onNext = {
                    picked = null
                    viewModel.nextMonth()
                },
                onJumpToCurrent = {
                    picked = null
                    viewModel.showCurrentMonth()
                }
            )
        }

        item {
            SectionCard(contentPadding = 14.dp) {
                WeekdayHeader()
                Spacer(Modifier.height(6.dp))
                MonthGrid(
                    month = state.month,
                    today = state.today,
                    selected = selected,
                    entriesByDate = state.entriesByDate,
                    onSelect = { date -> picked = date }
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MoneyTheme.colors.borderSubtle)
                Spacer(Modifier.height(10.dp))
                CalendarLegend()
            }
        }

        item { FilterRow(filter = filter, onChange = { filter = it }) }

        if (selected != null) {
            item {
                DayCard(
                    date = selected,
                    entries = state.entriesByDate[selected].orEmpty().filter(filter::matches)
                )
            }
        }

        val stillToCome = state.entriesByDate
            .filterKeys { !it.isBefore(state.today) || state.month.isAfter(currentMonth) }
            .values
            .flatten()
            .filter { it.isScheduled && filter.matches(it) }
            .sortedBy { it.date }

        when {
            state.isEmpty -> item {
                SectionCard {
                    Text(
                        // A finished month can only ever hold what happened, so saying
                        // nothing is "scheduled" in it would be answering a question the
                        // user did not ask.
                        if (state.isPastMonth) {
                            "Nothing was recorded in " + DateUtil.formatMonth(state.month) + "."
                        } else {
                            "Nothing is scheduled in " + DateUtil.formatMonth(state.month) + "."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoneyTheme.colors.textSecondary
                    )
                }
            }
            !state.isPastMonth && stillToCome.isNotEmpty() -> item {
                StillToComeCard(month = state.month, entries = stillToCome)
            }
            state.isPastMonth && selected == null -> item {
                EverythingCard(state.month, state.entriesByDate, filter)
            }
            else -> Unit
        }
    }
}

@Composable
private fun CalendarHeader(onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Null when this is a bottom-bar destination. A tab with a back arrow invites a tap
        // that goes somewhere the user did not come from.
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Text(
            "Calendar",
            style = MaterialTheme.typography.headlineLarge,
            color = MoneyTheme.colors.display
        )
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
                style = PillLabelStyle,
                color = MoneyTheme.colors.textTertiary
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
                    // Days of the neighbouring months fill the first and last rows, faded,
                    // so the grid keeps its shape and the weekday of the 1st is obvious.
                    val date = firstDay.plusDays((cellIndex - leadingBlanks).toLong())
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (YearMonth.from(date) == month) {
                            DayCell(
                                date = date,
                                today = today,
                                isSelected = date == selected,
                                entries = entriesByDate[date].orEmpty(),
                                onClick = { onSelect(date) }
                            )
                        } else {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MoneyTheme.colors.textTertiary.copy(alpha = 0.45f),
                                modifier = Modifier.clearAndSetSemantics { }
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
    today: LocalDate,
    isSelected: Boolean,
    entries: List<CalendarEntry>,
    onClick: () -> Unit
) {
    val colors = MoneyTheme.colors
    val isToday = date == today
    val isFuture = date.isAfter(today)
    val shape = RoundedCornerShape(12.dp)

    // Neutral movements carry no dot: a transfer changes no total, so marking it as money
    // out would say something about the month that is not true.
    val recordedIn = entries.any { !it.isScheduled && it.isInflow }
    val recordedOut = entries.any { !it.isScheduled && it.isOutflow }
    val projected = entries.any { it.isScheduled && !it.isOverdue }
    val overdue = entries.any { it.isOverdue }

    val background = when {
        isToday -> colors.display
        isSelected -> colors.accentSoft
        isFuture -> colors.sunken
        else -> Color.Transparent
    }

    // Read aloud, so it has to say what a sighted user gets from the dots: whether the
    // day holds anything, and whether any of it is late.
    val description = buildString {
        append(DateUtil.formatDate(date))
        if (isToday) append(", today")
        if (entries.isEmpty()) {
            append(", nothing")
        } else {
            append(", ${entries.size} ")
            append(if (entries.size == 1) "entry" else "entries")
            if (overdue) append(", something is overdue")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(background)
            .then(
                when {
                    isToday -> Modifier.border(2.dp, colors.accent, shape)
                    isSelected -> Modifier.border(1.dp, colors.accent.copy(alpha = 0.7f), shape)
                    isFuture && projected -> Modifier.dashedOutline(colors.forecast, 12.dp)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isToday -> Color.White
                    isFuture -> colors.textSecondary
                    else -> colors.textPrimary
                }
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Independent markers, matching the legend exactly. Late money is its own
                // dot rather than a recolouring, because a salary that has not arrived is
                // overdue too and would otherwise stay green.
                if (recordedIn) Dot(colors.positive)
                if (recordedOut) Dot(colors.negative)
                if (projected) Dot(if (isToday) colors.accent else colors.forecast)
                if (overdue) Dot(colors.accent)
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
private fun CalendarLegend() {
    val colors = MoneyTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LegendItem("Money in") { Dot(colors.positive) }
        LegendItem("Money out") { Dot(colors.negative) }
        LegendItem("Overdue") { Dot(colors.accent) }
        LegendItem("Projected") {
            Box(
                Modifier
                    .size(10.dp)
                    .dashedOutline(colors.forecast, 5.dp)
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, marker: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        marker()
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MoneyTheme.colors.textSecondary)
    }
}

/** What the lists below the grid show. The grid itself always shows everything. */
private enum class EntryFilter(val label: String) {
    ALL("All"),
    IN("Money in"),
    OUT("Money out"),
    OVERDUE("Overdue");

    fun matches(entry: CalendarEntry): Boolean = when (this) {
        ALL -> true
        IN -> entry.isInflow
        OUT -> entry.isOutflow
        OVERDUE -> entry.isOverdue
    }
}

@Composable
private fun FilterRow(filter: EntryFilter, onChange: (EntryFilter) -> Unit) {
    val colors = MoneyTheme.colors
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EntryFilter.entries.forEach { option ->
            val chosen = option == filter
            val marker = when (option) {
                EntryFilter.ALL -> colors.accent
                EntryFilter.IN -> colors.positive
                EntryFilter.OUT -> colors.negative
                EntryFilter.OVERDUE -> colors.warning
            }
            val shape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(if (chosen) MaterialTheme.colorScheme.primary else colors.card)
                    .then(if (chosen) Modifier else Modifier.border(1.dp, colors.cardBorder, shape))
                    .selectable(selected = chosen, role = Role.Tab, onClick = { onChange(option) })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!chosen) {
                    Dot(marker)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (chosen) MaterialTheme.colorScheme.onPrimary else colors.textPrimary
                )
                if (chosen) {
                    Spacer(Modifier.width(8.dp))
                    Dot(colors.accent)
                }
            }
        }
    }
}

/** Everything on one day: what happened first, then what is still expected. */
@Composable
private fun DayCard(date: LocalDate, entries: List<CalendarEntry>) {
    val colors = MoneyTheme.colors

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(colors.accent, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                DateUtil.formatWeekdayDate(date),
                style = MaterialTheme.typography.titleLarge,
                color = colors.display,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (entries.size == 1) "1 entry" else "${entries.size} entries",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
                modifier = Modifier
                    .background(colors.sunken, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        if (entries.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Nothing on this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        } else {
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = colors.borderSubtle)
                CalendarEntryRow(entry)
            }
        }
    }
}

@Composable
private fun CalendarEntryRow(entry: CalendarEntry) {
    val colors = MoneyTheme.colors
    val (icon, tint, well) = entryLook(entry)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconChip(icon, tint, well)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 2
            )
            Text(
                // A late balance sits on today's date to stay in view; this says how late.
                entry.originallyDue?.let { "Due " + DateUtil.formatDayMonth(it) + " · " + entry.subtitle }
                    ?: entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            EntryAmount(entry)
            Spacer(Modifier.height(4.dp))
            // A payment that has left the account and one that is merely due look the same
            // on a grid, and treating them alike is how somebody concludes a bill is paid
            // when it is not. The same vocabulary is used everywhere in the app.
            ConfidencePill(confidenceOf(entry))
        }
    }
}

/**
 * What is still expected this month, each in a tinted well that says what kind of thing it
 * is: an estimate in a dashed lavender outline, money arriving in teal, late money in
 * marigold, an ordinary scheduled payment in plain porcelain.
 */
@Composable
private fun StillToComeCard(month: YearMonth, entries: List<CalendarEntry>) {
    val colors = MoneyTheme.colors
    val shown = entries.take(STILL_TO_COME_ROWS)

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = colors.forecast,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Still to come in ${DateUtil.formatMonth(month).substringBefore(" ")}",
                style = MaterialTheme.typography.titleLarge,
                color = colors.display
            )
        }
        Spacer(Modifier.height(12.dp))
        shown.forEachIndexed { index, entry ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            UpcomingWell(entry)
        }
        if (entries.size > shown.size) {
            Spacer(Modifier.height(10.dp))
            Text(
                "and ${entries.size - shown.size} more",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

private const val STILL_TO_COME_ROWS = 6

@Composable
private fun UpcomingWell(entry: CalendarEntry) {
    val colors = MoneyTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val (icon, tint, well) = entryLook(entry)

    val container = when {
        entry.isOverdue -> Modifier
            .background(colors.accentSoft, shape)
            .border(1.dp, colors.warningBorder, shape)
        entry.isEstimate -> Modifier
            .background(colors.forecastContainer.copy(alpha = 0.6f), shape)
            .dashedOutline(colors.forecast, 18.dp)
        entry.isInflow -> Modifier
            .background(colors.positiveContainer.copy(alpha = 0.7f), shape)
            .border(1.dp, colors.positiveBorder, shape)
        else -> Modifier
            .background(colors.sunken, shape)
            .border(1.dp, colors.cardBorder, shape)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(container)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconChip(icon, tint, if (entry.isInflow || entry.isEstimate) colors.card else well)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    DateUtil.formatDayMonth(entry.originallyDue ?: entry.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            EntryAmount(entry)
            Spacer(Modifier.height(4.dp))
            ConfidencePill(confidenceOf(entry))
        }
    }
}

/** Past months: everything recorded, day by day. */
@Composable
private fun EverythingCard(
    month: YearMonth,
    entriesByDate: Map<LocalDate, List<CalendarEntry>>,
    filter: EntryFilter
) {
    val colors = MoneyTheme.colors
    SectionCard(title = "Everything in ${DateUtil.formatMonth(month)}") {
        entriesByDate.keys.sorted().forEach { date ->
            val entries = entriesByDate.getValue(date).filter(filter::matches)
            if (entries.isNotEmpty()) {
                Text(
                    DateUtil.formatWeekdayDate(date),
                    style = PillLabelStyle,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
                entries.forEach { entry -> CalendarEntryRow(entry) }
            }
        }
    }
}

@Composable
private fun EntryAmount(entry: CalendarEntry) {
    val colors = MoneyTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        // An estimate says so in its figure too, not only in its pill: "~₹2,400" in lavender,
        // as on the home screen, rather than a signed figure stated as though it were exact.
        if (entry.isEstimate) {
            Text("~", style = MaterialTheme.typography.titleMedium, color = colors.forecast)
        }
        MoneyText(
            money = when {
                entry.isEstimate -> entry.amount
                entry.isInflow -> entry.amount
                entry.isOutflow -> -entry.amount
                else -> entry.amount
            },
            style = MaterialTheme.typography.titleMedium,
            showSign = !entry.isEstimate && (entry.isInflow || entry.isOutflow),
            color = when {
                entry.isEstimate -> colors.forecast
                entry.isInflow -> colors.positive
                entry.isOutflow -> colors.negative
                else -> colors.neutral
            }
        )
    }
}

private fun confidenceOf(entry: CalendarEntry): Confidence = when {
    entry.isOverdue -> Confidence.OVERDUE
    entry.isEstimate -> Confidence.ESTIMATED
    entry.isScheduled -> Confidence.UPCOMING
    else -> Confidence.RECORDED
}

@Composable
private fun entryLook(entry: CalendarEntry): Triple<ImageVector, Color, Color> {
    val colors = MoneyTheme.colors
    return when {
        entry.isEstimate -> Triple(Icons.Default.Bolt, colors.forecast, colors.forecastContainer)
        entry.isInflow -> Triple(Icons.Default.Payments, colors.positive, colors.positiveContainer)
        entry.isOutflow && entry.isScheduled ->
            Triple(Icons.Default.EventRepeat, MaterialTheme.colorScheme.primary, colors.forecastContainer)
        entry.isOutflow -> Triple(Icons.Default.ShoppingBasket, colors.textSecondary, colors.sunken)
        else -> Triple(Icons.Default.SwapHoriz, colors.neutral, colors.sunken)
    }
}

@Composable
private fun IconChip(icon: ImageVector, tint: Color, well: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(well, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
