package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.model.Person
import com.moneyplanner.domain.model.PersonLedgerEntry
import com.moneyplanner.domain.model.Settlement
import com.moneyplanner.domain.model.SettlementDirection
import java.time.LocalDate

/**
 * Works out who owes whom, and how much is left after part payments.
 *
 * The balance is always recomputed from the ledger entries and the settlements. Nothing
 * is stored as a running total, so a balance can never drift away from the history that
 * is supposed to explain it, and editing or deleting an old entry corrects everything
 * that follows without a repair step.
 *
 * Sign convention: a positive balance means the other person owes the user.
 */
object SettlementCalculator {

    /**
     * The net position with one person.
     *
     * Obligations count towards the balance, settlements count against it, and each side
     * is signed by its own direction, so a part payment simply reduces the outstanding
     * amount and an overpayment carries the balance past zero into the other direction.
     */
    fun balanceFor(
        entries: List<PersonLedgerEntry>,
        settlements: List<Settlement>
    ): Money {
        val owed = entries.sumOfMoney { entry ->
            when (entry.direction) {
                LedgerDirection.THEY_OWE_ME -> entry.amount
                LedgerDirection.I_OWE_THEM -> -entry.amount
            }
        }
        val settled = settlements.sumOfMoney { settlement ->
            when (settlement.direction) {
                // Money received reduces what they owe me.
                SettlementDirection.RECEIVED_FROM_THEM -> -settlement.amount
                // Money paid reduces what I owe them, moving the balance back up.
                SettlementDirection.PAID_TO_THEM -> settlement.amount
            }
        }
        return owed + settled
    }

    /** Balances for every person, keyed by person id. */
    fun balancesByPerson(
        entries: List<PersonLedgerEntry>,
        settlements: List<Settlement>
    ): Map<Long, Money> {
        val entriesByPerson = entries.groupBy { it.personId }
        val settlementsByPerson = settlements.groupBy { it.personId }
        val ids = entriesByPerson.keys + settlementsByPerson.keys
        return ids.associateWith { id ->
            balanceFor(
                entriesByPerson[id].orEmpty(),
                settlementsByPerson[id].orEmpty()
            )
        }
    }

    /** A summary for every person the user has recorded anything against. */
    fun summaries(
        people: List<Person>,
        entries: List<PersonLedgerEntry>,
        settlements: List<Settlement>
    ): List<PersonBalanceSummary> {
        val balances = balancesByPerson(entries, settlements)
        val entriesByPerson = entries.groupBy { it.personId }
        val settlementsByPerson = settlements.groupBy { it.personId }

        return people.map { person ->
            val personEntries = entriesByPerson[person.id].orEmpty()
            val personSettlements = settlementsByPerson[person.id].orEmpty()
            PersonBalanceSummary(
                person = person,
                balance = balances[person.id] ?: Money.ZERO,
                totalTheyOwedMe = personEntries
                    .filter { it.direction == LedgerDirection.THEY_OWE_ME }
                    .sumOfMoney { it.amount },
                totalIOwedThem = personEntries
                    .filter { it.direction == LedgerDirection.I_OWE_THEM }
                    .sumOfMoney { it.amount },
                totalReceived = personSettlements
                    .filter { it.direction == SettlementDirection.RECEIVED_FROM_THEM }
                    .sumOfMoney { it.amount },
                totalPaid = personSettlements
                    .filter { it.direction == SettlementDirection.PAID_TO_THEM }
                    .sumOfMoney { it.amount },
                entryCount = personEntries.size,
                lastActivity = (personEntries.map { it.date } + personSettlements.map { it.date })
                    .maxOrNull()
            )
        }
    }

    /** Everything the user is still expecting to receive, across all people. */
    fun totalReceivable(entries: List<PersonLedgerEntry>, settlements: List<Settlement>): Money =
        balancesByPerson(entries, settlements).values
            .filter { it.isPositive }
            .sumOfMoney()

    /** Everything the user still has to pay back, expressed as a positive amount. */
    fun totalPayable(entries: List<PersonLedgerEntry>, settlements: List<Settlement>): Money =
        balancesByPerson(entries, settlements).values
            .filter { it.isNegative }
            .sumOfMoney()
            .abs()

    /**
     * Spreads settlements across the individual obligations, oldest first.
     *
     * This is what lets the person screen show which specific loans are cleared and
     * which are still open, rather than only a single net figure. Settlements are
     * applied to entries pointing the same way, ordered by date and then by id so the
     * result is identical on every run. Anything left over is reported as unallocated
     * rather than being forced onto an unrelated entry.
     */
    fun allocate(
        entries: List<PersonLedgerEntry>,
        settlements: List<Settlement>
    ): AllocationResult {
        val sortedEntries = entries.sortedWith(compareBy({ it.date }, { it.id }))
        val remaining = sortedEntries.associate { it.id to it.amount }.toMutableMap()

        fun apply(direction: LedgerDirection, pot: Money): Money {
            var pool = pot
            for (entry in sortedEntries) {
                if (!pool.isPositive) break
                if (entry.direction != direction) continue
                val outstanding = remaining[entry.id] ?: Money.ZERO
                if (!outstanding.isPositive) continue
                val applied = if (pool < outstanding) pool else outstanding
                remaining[entry.id] = outstanding - applied
                pool -= applied
            }
            return pool
        }

        val received = settlements
            .filter { it.direction == SettlementDirection.RECEIVED_FROM_THEM }
            .sumOfMoney { it.amount }
        val paid = settlements
            .filter { it.direction == SettlementDirection.PAID_TO_THEM }
            .sumOfMoney { it.amount }

        val unallocatedReceived = apply(LedgerDirection.THEY_OWE_ME, received)
        val unallocatedPaid = apply(LedgerDirection.I_OWE_THEM, paid)

        val rows = sortedEntries.map { entry ->
            val outstanding = remaining[entry.id] ?: Money.ZERO
            EntryAllocation(
                entry = entry,
                settledAmount = entry.amount - outstanding,
                outstandingAmount = outstanding,
                isFullySettled = !outstanding.isPositive
            )
        }

        return AllocationResult(
            rows = rows,
            unallocatedReceived = unallocatedReceived,
            unallocatedPaid = unallocatedPaid,
            balance = balanceFor(entries, settlements)
        )
    }
}

data class PersonBalanceSummary(
    val person: Person,
    /** Positive when they owe the user, negative when the user owes them. */
    val balance: Money,
    val totalTheyOwedMe: Money,
    val totalIOwedThem: Money,
    val totalReceived: Money,
    val totalPaid: Money,
    val entryCount: Int,
    val lastActivity: LocalDate?
) {
    val isSettled: Boolean get() = balance.isZero
    val theyOweMe: Boolean get() = balance.isPositive
    val iOweThem: Boolean get() = balance.isNegative
    val displayAmount: Money get() = balance.abs()
}

data class EntryAllocation(
    val entry: PersonLedgerEntry,
    val settledAmount: Money,
    val outstandingAmount: Money,
    val isFullySettled: Boolean
)

data class AllocationResult(
    val rows: List<EntryAllocation>,
    /**
     * Money received beyond what the person actually owed. It is surfaced rather than
     * absorbed, because an unexplained extra payment is usually a data entry mistake or
     * an advance that the user will want to record properly.
     */
    val unallocatedReceived: Money,
    val unallocatedPaid: Money,
    val balance: Money
) {
    val hasOverpayment: Boolean
        get() = unallocatedReceived.isPositive || unallocatedPaid.isPositive
}
