package com.moneyplanner.ui.screens.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.data.repo.FamilyRepository
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.data.repo.VehicleRepository
import com.moneyplanner.domain.calc.EmiCalculator
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.FamilyMember
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.Relation
import com.moneyplanner.domain.model.Vehicle
import com.moneyplanner.domain.model.VehicleType
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/**
 * What each vehicle actually costs to keep.
 *
 * The monthly figure is assembled from three separate sources so nothing is counted
 * twice: the loan installment comes from the EMI, yearly costs such as insurance come in
 * at their monthly share, and running costs are averaged from expenses tagged to the
 * vehicle. Expenses created by paying the vehicle EMI or its insurance carry a link back
 * to that obligation, and those are excluded from running costs because they are already
 * represented by the first two figures.
 */
@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    val state: StateFlow<VehiclesState> = combine(
        vehicleRepository.all,
        snapshotRepository.snapshot
    ) { vehicles, snapshot ->
        val rows = vehicles.map { vehicle -> buildRow(vehicle, snapshot) }
        VehiclesState(
            vehicles = rows,
            totalMonthlyCost = rows.sumOfMoney { it.monthlyCost },
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VehiclesState()
    )

    private fun buildRow(vehicle: Vehicle, snapshot: FinancialSnapshot): VehicleRow {
        val lookbackMonths = 3
        val recentMonths = (1..lookbackMonths)
            .map { snapshot.currentMonth.minusMonths(it.toLong()) }

        val emiMonthly = snapshot.emis
            .filter { it.vehicleId == vehicle.id && it.isActive }
            .filterNot { EmiCalculator.isCompleted(it, snapshot.emiPayments) }
            .sumOfMoney { emi ->
                if (emi.frequency.isWeekly) emi.emiAmount * 4
                else emi.emiAmount.divideRounded(emi.frequency.monthsPerPeriod)
            }

        val annualMonthly = snapshot.annualExpenses
            .filter { it.vehicleId == vehicle.id && it.isActive }
            .sumOfMoney { it.monthlyReserve }

        val runningExpenses = snapshot.expenses.filter {
            it.vehicleId == vehicle.id &&
                it.linkType == ExpenseLinkType.NONE &&
                YearMonth.from(it.date) in recentMonths
        }
        val monthsWithData = recentMonths.count { month ->
            runningExpenses.any { YearMonth.from(it.date) == month }
        }
        val runningMonthly = if (monthsWithData == 0) {
            Money.ZERO
        } else {
            runningExpenses.sumOfMoney { it.amount }.divideRounded(monthsWithData)
        }

        return VehicleRow(
            vehicle = vehicle,
            emiMonthly = emiMonthly,
            annualMonthly = annualMonthly,
            runningMonthly = runningMonthly,
            monthlyCost = emiMonthly + annualMonthly + runningMonthly
        )
    }

    fun delete(id: Long) {
        viewModelScope.launch { vehicleRepository.delete(id) }
    }
}

data class VehicleRow(
    val vehicle: Vehicle,
    val emiMonthly: Money,
    val annualMonthly: Money,
    val runningMonthly: Money,
    val monthlyCost: Money
)

data class VehiclesState(
    val vehicles: List<VehicleRow> = emptyList(),
    val totalMonthlyCost: Money = Money.ZERO,
    val isLoading: Boolean = true
)

@HiltViewModel
class VehicleEditorViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _form = MutableStateFlow(VehicleForm())
    val form: StateFlow<VehicleForm> = _form.asStateFlow()

    fun updateName(v: String) = _form.update { it.copy(name = v, nameError = null) }
    fun updateType(v: VehicleType) = _form.update { it.copy(type = v) }
    fun updateRegistration(v: String) = _form.update { it.copy(registration = v) }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        if (current.name.isBlank()) {
            _form.update { it.copy(nameError = "Give this vehicle a name") }
            return
        }
        viewModelScope.launch {
            vehicleRepository.add(
                Vehicle(
                    id = 0,
                    name = current.name.trim(),
                    type = current.type,
                    registrationNumber = current.registration.ifBlank { null }
                )
            )
            onSaved()
        }
    }
}

data class VehicleForm(
    val name: String = "",
    val type: VehicleType = VehicleType.BIKE,
    val registration: String = "",
    val nameError: String? = null
) {
    val canSave: Boolean get() = name.isNotBlank()
}

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val familyRepository: FamilyRepository,
    snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    val state: StateFlow<FamilyState> = combine(
        familyRepository.all,
        snapshotRepository.snapshot
    ) { members, snapshot ->
        val month = snapshot.currentMonth
        val rows = members.map { member ->
            FamilyRow(
                member = member,
                spentThisMonth = snapshot.expenses
                    .filter { it.familyMemberId == member.id && YearMonth.from(it.date) == month }
                    .sumOfMoney { it.amount }
            )
        }
        FamilyState(
            members = rows,
            totalThisMonth = rows.sumOfMoney { it.spentThisMonth },
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FamilyState()
    )

    fun add(name: String, relation: Relation) {
        viewModelScope.launch { familyRepository.add(name, relation) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { familyRepository.delete(id) }
    }
}

data class FamilyRow(val member: FamilyMember, val spentThisMonth: Money)

data class FamilyState(
    val members: List<FamilyRow> = emptyList(),
    val totalThisMonth: Money = Money.ZERO,
    val isLoading: Boolean = true
)
