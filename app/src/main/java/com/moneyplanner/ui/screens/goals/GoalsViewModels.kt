package com.moneyplanner.ui.screens.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.data.repo.SavingsRepository
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.domain.calc.EmergencyFundCalculator
import com.moneyplanner.domain.calc.EmergencyFundStatus
import com.moneyplanner.domain.calc.GoalProgress
import com.moneyplanner.domain.calc.SavingsCalculator
import com.moneyplanner.domain.model.GoalPriority
import com.moneyplanner.domain.model.SavingsContribution
import com.moneyplanner.domain.model.SavingsGoal
import com.moneyplanner.ui.nav.Routes
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    val state: StateFlow<GoalsState> = snapshotRepository.snapshot.map { snapshot ->
        val progress = SavingsCalculator.allProgress(snapshot)
            .sortedWith(
                compareBy<GoalProgress> { it.isAchieved }
                    .thenByDescending { it.goal.priority.weight }
            )
        GoalsState(
            goals = progress,
            totalSaved = progress.sumOfMoney { it.saved },
            totalRemaining = progress.sumOfMoney { it.remaining },
            totalMonthlyRequired = progress.sumOfMoney { it.requiredMonthlySaving },
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GoalsState()
    )
}

data class GoalsState(
    val goals: List<GoalProgress> = emptyList(),
    val totalSaved: Money = Money.ZERO,
    val totalRemaining: Money = Money.ZERO,
    val totalMonthlyRequired: Money = Money.ZERO,
    val isLoading: Boolean = true
)

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    private val savingsRepository: SavingsRepository,
    private val today: TodayProvider,
    savedStateHandle: SavedStateHandle,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    private val goalId: Long = savedStateHandle.get<String>(Routes.ARG_GOAL_ID)?.toLongOrNull() ?: 0L

    val state: StateFlow<GoalDetailState> = combine(
        savingsRepository.byId(goalId),
        savingsRepository.contributionsFor(goalId)
    ) { goal, contributions ->
        GoalDetailState(
            progress = goal?.let {
                SavingsCalculator.progressOf(it, contributions, today.today())
            },
            contributions = contributions,
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GoalDetailState()
    )

    private val _contributionText = MutableStateFlow("")
    val contributionText: StateFlow<String> = _contributionText.asStateFlow()

    fun updateContributionText(value: String) {
        _contributionText.value = value
    }

    fun contribute() {
        val amount = Money.parseOrNull(_contributionText.value) ?: return
        if (!amount.isPositive) return
        viewModelScope.launch {
            savingsRepository.contribute(goalId, amount)
            _contributionText.value = ""
        }
    }

    fun withdraw() {
        val amount = Money.parseOrNull(_contributionText.value) ?: return
        if (!amount.isPositive) return
        viewModelScope.launch {
            savingsRepository.withdraw(goalId, amount)
            _contributionText.value = ""
        }
    }

    fun deleteContribution(id: Long) {
        viewModelScope.launch { savingsRepository.deleteContribution(id) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            savingsRepository.delete(goalId)
            onDeleted()
        }
    }
}

data class GoalDetailState(
    val progress: GoalProgress? = null,
    val contributions: List<SavingsContribution> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class GoalEditorViewModel @Inject constructor(
    private val savingsRepository: SavingsRepository,
    private val today: TodayProvider
) : ViewModel() {

    private val _form = MutableStateFlow(
        GoalForm(suggestedTargetDate = today.today().plusYears(1))
    )
    val form: StateFlow<GoalForm> = _form.asStateFlow()

    fun updateName(value: String) = _form.update { it.copy(name = value, nameError = null) }
    fun updateTarget(value: String) = _form.update { it.copy(targetText = value, targetError = null) }
    fun updateTargetDate(value: LocalDate?) = _form.update { it.copy(targetDate = value) }
    fun updatePriority(value: GoalPriority) = _form.update { it.copy(priority = value) }
    fun updateNotes(value: String) = _form.update { it.copy(notes = value) }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        val target = Money.parseOrNull(current.targetText)
        if (current.name.isBlank()) {
            _form.update { it.copy(nameError = "Give this goal a name") }
            return
        }
        if (target == null || !target.isPositive) {
            _form.update { it.copy(targetError = "Enter a target greater than zero") }
            return
        }
        viewModelScope.launch {
            savingsRepository.add(
                SavingsGoal(
                    id = 0,
                    name = current.name.trim(),
                    targetAmount = target,
                    targetDate = current.targetDate,
                    priority = current.priority,
                    isEmergencyFund = false,
                    isAchieved = false,
                    notes = current.notes.trim()
                )
            )
            onSaved()
        }
    }

    companion object {
        val SUGGESTIONS = listOf(
            "New phone", "Bike", "Car", "Vacation", "Child education",
            "Wedding", "Home", "Laptop", "Festival"
        )
    }
}

data class GoalForm(
    val name: String = "",
    val targetText: String = "",
    val targetDate: LocalDate? = null,
    val priority: GoalPriority = GoalPriority.MEDIUM,
    val notes: String = "",
    val nameError: String? = null,
    val targetError: String? = null,
    val suggestedTargetDate: LocalDate = LocalDate.now().plusYears(1)
) {
    val canSave: Boolean
        get() = name.isNotBlank() && Money.parseOrNull(targetText)?.isPositive == true

    /** Live preview of the monthly commitment implied by the target and date. */
    val requiredMonthly: Money
        get() {
            val target = Money.parseOrNull(targetText) ?: return Money.ZERO
            val date = targetDate ?: return Money.ZERO
            val months = ChronoUnit.MONTHS.between(
                java.time.YearMonth.now(),
                java.time.YearMonth.from(date)
            ).toInt().coerceAtLeast(1)
            return target.divideRounded(months)
        }
}

/**
 * The emergency fund.
 *
 * It gets its own screen because it is built differently from other goals: the target is
 * not a number the user picks, it is derived from what their own essential expenses
 * actually cost.
 */
@HiltViewModel
class EmergencyFundViewModel @Inject constructor(
    private val savingsRepository: SavingsRepository,
    private val profileRepository: com.moneyplanner.data.repo.ProfileRepository,
    snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    val state: StateFlow<EmergencyFundState> = snapshotRepository.snapshot.map { snapshot ->
        EmergencyFundState(
            status = EmergencyFundCalculator.calculate(snapshot),
            monthsSetting = snapshot.profile.emergencyFundMonths,
            profile = snapshot.profile,
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EmergencyFundState()
    )

    private val _contributionText = MutableStateFlow("")
    val contributionText: StateFlow<String> = _contributionText.asStateFlow()

    fun updateContributionText(value: String) {
        _contributionText.value = value
    }

    fun setMonths(months: Int) {
        viewModelScope.launch {
            val profile = state.value.profile.copy(emergencyFundMonths = months)
            profileRepository.saveProfile(profile)
        }
    }

    /** Creates the fund on first use, using the calculated target. */
    fun contribute() {
        val amount = Money.parseOrNull(_contributionText.value) ?: return
        if (!amount.isPositive) return
        viewModelScope.launch {
            val goalId = savingsRepository.ensureEmergencyFund(state.value.status.targetAmount)
            savingsRepository.contribute(goalId, amount)
            _contributionText.value = ""
        }
    }

    fun createFund() {
        viewModelScope.launch {
            savingsRepository.ensureEmergencyFund(state.value.status.targetAmount)
        }
    }
}

data class EmergencyFundState(
    val status: EmergencyFundStatus = EmergencyFundStatus(
        monthlyEssentialExpenses = Money.ZERO,
        essentialBills = Money.ZERO,
        emiBurden = Money.ZERO,
        essentialEverydaySpend = Money.ZERO,
        annualReserve = Money.ZERO,
        monthsOfCover = 6,
        targetAmount = Money.ZERO,
        currentAmount = Money.ZERO,
        remainingAmount = Money.ZERO,
        progressFraction = 0f,
        hasGoal = false,
        goalId = null,
        monthsCovered = 0.0
    ),
    val monthsSetting: Int = 6,
    val profile: com.moneyplanner.domain.model.UserProfile = com.moneyplanner.domain.model.UserProfile(),
    val isLoading: Boolean = true
)
