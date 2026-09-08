package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.model.SplitType

/**
 * Splits a shared bill and works out what it means for the user's balances.
 *
 * Two properties are guaranteed and are what the tests hold this to:
 *
 *  - the shares always add up to the total, to the exact paisa, whichever split is used;
 *  - the user's own share is never turned into a debt owed to themselves.
 *
 * When somebody else pays the bill, only the user's own share becomes a balance. What the
 * other participants owe that person is between them, and recording it here would clutter
 * the user's ledger with debts that are not theirs to chase.
 */
object SplitCalculator {

    /**
     * @param participants everyone sharing the bill. A null id is the user.
     * @param paidByPersonId who actually paid; null means the user paid.
     */
    fun split(
        totalAmount: Money,
        splitType: SplitType,
        participants: List<SplitParticipant>,
        paidByPersonId: Long?
    ): SplitResult {
        require(participants.isNotEmpty()) { "A shared expense needs at least one participant" }

        val shares = when (splitType) {
            SplitType.EQUAL -> equalShares(totalAmount, participants)
            SplitType.EXACT -> exactShares(participants)
            SplitType.PERCENTAGE -> percentageShares(totalAmount, participants)
            SplitType.FULL_ON_ONE -> fullOnOne(totalAmount, participants)
        }

        val myShare = shares.firstOrNull { it.personId == null }?.amount ?: Money.ZERO

        val obligations = if (paidByPersonId == null) {
            // The user paid, so everybody else owes their share back.
            shares
                .filter { it.personId != null && it.amount.isPositive }
                .map { share ->
                    SplitObligation(
                        personId = share.personId!!,
                        amount = share.amount,
                        direction = LedgerDirection.THEY_OWE_ME
                    )
                }
        } else {
            // Somebody else paid, so the user owes them their own share and nothing more.
            if (myShare.isPositive) {
                listOf(
                    SplitObligation(
                        personId = paidByPersonId,
                        amount = myShare,
                        direction = LedgerDirection.I_OWE_THEM
                    )
                )
            } else {
                emptyList()
            }
        }

        return SplitResult(
            shares = shares,
            myShare = myShare,
            obligations = obligations,
            /**
             * What the user actually spent out of pocket. When they paid the bill it is
             * the whole amount; the money coming back is tracked as a balance, not as a
             * reduction of the expense.
             */
            amountPaidByUser = if (paidByPersonId == null) totalAmount else Money.ZERO
        )
    }

    private fun equalShares(total: Money, participants: List<SplitParticipant>): List<SplitShare> {
        val amounts = total.splitEvenly(participants.size)
        return participants.mapIndexed { index, participant ->
            SplitShare(participant.personId, amounts[index], null)
        }
    }

    private fun exactShares(participants: List<SplitParticipant>): List<SplitShare> =
        participants.map { SplitShare(it.personId, it.exactAmount ?: Money.ZERO, null) }

    /**
     * Percentage split with the rounding remainder given to the largest share, so the
     * parts still add up to the total exactly.
     */
    private fun percentageShares(
        total: Money,
        participants: List<SplitParticipant>
    ): List<SplitShare> {
        val raw = participants.map { participant ->
            val percent = participant.percent ?: 0.0
            SplitShare(participant.personId, total.percent(percent), percent)
        }
        val difference = total - raw.sumOfMoney { it.amount }
        if (difference.isZero) return raw

        val largestIndex = raw.indices.maxByOrNull { raw[it].amount.paise } ?: return raw
        return raw.mapIndexed { index, share ->
            if (index == largestIndex) share.copy(amount = share.amount + difference) else share
        }
    }

    /** One participant carries the whole bill; everyone else has a zero share. */
    private fun fullOnOne(total: Money, participants: List<SplitParticipant>): List<SplitShare> {
        val bearer = participants.firstOrNull { it.bearsFullAmount } ?: participants.first()
        return participants.map { participant ->
            SplitShare(
                personId = participant.personId,
                amount = if (participant.personId == bearer.personId) total else Money.ZERO,
                percent = null
            )
        }
    }

    /** Validates a custom split before it is saved. */
    fun validateExact(total: Money, participants: List<SplitParticipant>): SplitValidation {
        val sum = participants.sumOfMoney { it.exactAmount ?: Money.ZERO }
        return when {
            sum == total -> SplitValidation.Valid
            sum < total -> SplitValidation.Short(total - sum)
            else -> SplitValidation.Over(sum - total)
        }
    }

    fun validatePercentages(participants: List<SplitParticipant>): SplitValidation {
        val sum = participants.sumOf { it.percent ?: 0.0 }
        val difference = sum - 100.0
        return when {
            kotlin.math.abs(difference) < 0.001 -> SplitValidation.Valid
            difference < 0 -> SplitValidation.ShortPercent(-difference)
            else -> SplitValidation.OverPercent(difference)
        }
    }
}

data class SplitParticipant(
    /** Null identifies the user themselves. */
    val personId: Long?,
    val exactAmount: Money? = null,
    val percent: Double? = null,
    val bearsFullAmount: Boolean = false
)

data class SplitShare(
    val personId: Long?,
    val amount: Money,
    val percent: Double?
)

data class SplitObligation(
    val personId: Long,
    val amount: Money,
    val direction: LedgerDirection
)

data class SplitResult(
    val shares: List<SplitShare>,
    val myShare: Money,
    val obligations: List<SplitObligation>,
    val amountPaidByUser: Money
) {
    val total: Money get() = shares.sumOfMoney { it.amount }
}

sealed interface SplitValidation {
    data object Valid : SplitValidation
    data class Short(val by: Money) : SplitValidation
    data class Over(val by: Money) : SplitValidation
    data class ShortPercent(val by: Double) : SplitValidation
    data class OverPercent(val by: Double) : SplitValidation
}
