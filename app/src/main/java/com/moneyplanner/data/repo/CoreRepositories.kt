package com.moneyplanner.data.repo

import com.moneyplanner.core.money.Money
import com.moneyplanner.data.db.DefaultData
import com.moneyplanner.data.db.dao.CategoryDao
import com.moneyplanner.data.db.dao.FamilyDao
import com.moneyplanner.data.db.dao.ProfileDao
import com.moneyplanner.data.db.dao.VehicleDao
import com.moneyplanner.data.db.entity.BalanceAdjustmentEntity
import com.moneyplanner.data.db.entity.CategoryEntity
import com.moneyplanner.data.db.entity.FamilyMemberEntity
import com.moneyplanner.data.db.entity.UserProfileEntity
import com.moneyplanner.data.mapper.toDomain
import com.moneyplanner.data.mapper.toEntity
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountTransfer
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.CategoryType
import com.moneyplanner.domain.model.FamilyMember
import com.moneyplanner.domain.model.Relation
import com.moneyplanner.domain.model.UserProfile
import com.moneyplanner.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies the current date, so date-dependent behaviour can be pinned in tests. */
interface TodayProvider {
    fun today(): LocalDate
}

@Singleton
class SystemTodayProvider @Inject constructor() : TodayProvider {
    override fun today(): LocalDate = LocalDate.now()
}

@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val categoryDao: CategoryDao,
    private val familyDao: FamilyDao,
    private val today: TodayProvider
) {
    val profile: Flow<UserProfile> =
        dao.observeProfile().map { it?.toDomain() ?: UserProfile() }

    val accounts: Flow<List<Account>> =
        dao.observeAccounts().map { list -> list.map { it.toDomain() } }

    val adjustments: Flow<List<com.moneyplanner.domain.model.BalanceAdjustment>> =
        dao.observeAdjustments().map { list -> list.map { it.toDomain() } }

    val transfers: Flow<List<AccountTransfer>> =
        dao.observeTransfers().map { list -> list.map { it.toDomain() } }

    suspend fun deleteAccount(account: Account) = dao.deleteAccount(account.toEntity())

    /**
     * Moves money between two of the user's own accounts.
     *
     * Refuses a transfer to the same account, which is a data entry slip rather than a
     * meaningful record, and refuses a non-positive amount. Direction is carried by which
     * account is named first, so a negative amount would silently mean the opposite of
     * what the user typed.
     */
    suspend fun transfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Money,
        on: LocalDate = today.today(),
        notes: String = ""
    ): Long? {
        if (fromAccountId == toAccountId) return null
        if (!amount.isPositive) return null
        return dao.insertTransfer(
            AccountTransfer(
                id = 0,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                date = on,
                notes = notes
            ).toEntity()
        )
    }

    suspend fun deleteTransfer(id: Long) = dao.deleteTransferById(id)

    suspend fun saveProfile(profile: UserProfile) {
        val existing = dao.getProfile()
        dao.upsertProfile(
            UserProfileEntity(
                displayName = profile.displayName,
                emergencyFundMonths = profile.emergencyFundMonths,
                monthStartDay = profile.monthStartDay,
                onboardingCompleted = profile.onboardingCompleted,
                createdAtEpochDay = existing?.createdAtEpochDay ?: today.today().toEpochDay()
            )
        )
    }

    suspend fun addAccount(account: Account): Long = dao.insertAccount(account.toEntity())

    suspend fun updateAccount(account: Account) = dao.updateAccount(account.toEntity())

    /**
     * Records what the user says their real balance is today.
     *
     * Rather than overwriting the opening balance and losing the history behind it, the
     * difference between the computed balance and the stated balance is stored as an
     * explicit correction. The balance stays fully derivable and the adjustment is
     * visible to the user afterwards.
     */
    suspend fun reconcileBalance(
        computedBalance: Money,
        statedBalance: Money,
        reason: String = "Balance updated by you"
    ) {
        val delta = statedBalance - computedBalance
        if (delta.isZero) return
        val date = today.today()
        dao.insertAdjustment(
            BalanceAdjustmentEntity(
                accountId = dao.getAllAccounts().firstOrNull()?.id,
                deltaPaise = delta.paise,
                dateEpochDay = date.toEpochDay(),
                reason = reason,
                createdAtEpochDay = date.toEpochDay()
            )
        )
    }

    /**
     * Creates the starting structure on first run: the standard categories, one account
     * and the user's own family entry. No amounts and no transactions are inserted, so a
     * fresh install genuinely starts at zero.
     */
    suspend fun seedIfEmpty() {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(DefaultData.categories())
        }
        if (dao.accountCount() == 0) {
            dao.insertAccount(DefaultData.defaultAccount(today.today()))
            dao.insertAccount(DefaultData.defaultCashAccount(today.today()))
        }
        if (familyDao.getAll().isEmpty()) {
            familyDao.insert(DefaultData.defaultFamilyMember())
        }
        if (dao.getProfile() == null) {
            dao.upsertProfile(
                UserProfileEntity(createdAtEpochDay = today.today().toEpochDay())
            )
        }
    }

    /**
     * Gives an existing install the cash account that fresh installs now get.
     *
     * Runs once, gated by a flag the caller owns, rather than on every launch: a user who
     * decides they do not track cash and archives the account should not find it back the
     * next morning. Nothing is migrated into it — the account starts empty, and the user
     * records what they are actually holding as an opening balance or a withdrawal.
     */
    suspend fun ensureCashAccount(): Long? {
        if (dao.accountCountOfType(AccountType.CASH.name) > 0) return null
        return dao.insertAccount(DefaultData.defaultCashAccount(today.today()))
    }
}

@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao
) {
    val all: Flow<List<Category>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    val expenseCategories: Flow<List<Category>> =
        dao.observeByType(CategoryType.EXPENSE.name).map { list -> list.map { it.toDomain() } }

    val incomeCategories: Flow<List<Category>> =
        dao.observeByType(CategoryType.INCOME.name).map { list -> list.map { it.toDomain() } }

    val everything: Flow<List<Category>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun add(
        name: String,
        type: CategoryType,
        colorHex: String,
        iconKey: String,
        isEssential: Boolean
    ): Long = dao.insert(
        CategoryEntity(
            name = name.trim(),
            type = type.name,
            iconKey = iconKey,
            colorHex = colorHex,
            isEssential = isEssential,
            isCustom = true,
            sortOrder = 1000
        )
    )

    suspend fun update(category: Category) = dao.update(category.toEntity())

    /**
     * Categories that already have expenses against them are archived rather than
     * deleted, so historic records keep the label they were filed under.
     */
    suspend fun removeOrArchive(category: Category): CategoryRemoval {
        val inUse = dao.expenseCountFor(category.id)
        return if (inUse > 0) {
            dao.update(category.copy(isArchived = true).toEntity())
            CategoryRemoval.Archived(inUse)
        } else {
            dao.deleteById(category.id)
            CategoryRemoval.Deleted
        }
    }

    suspend fun findByName(name: String, type: CategoryType) =
        dao.findByName(name, type.name)?.toDomain()
}

sealed interface CategoryRemoval {
    data object Deleted : CategoryRemoval
    data class Archived(val expenseCount: Int) : CategoryRemoval
}

@Singleton
class VehicleRepository @Inject constructor(
    private val dao: VehicleDao
) {
    val all: Flow<List<Vehicle>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    fun byId(id: Long): Flow<Vehicle?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun add(vehicle: Vehicle): Long = dao.insert(vehicle.toEntity())
    suspend fun update(vehicle: Vehicle) = dao.update(vehicle.toEntity())
    suspend fun delete(id: Long) = dao.deleteById(id)
}

@Singleton
class FamilyRepository @Inject constructor(
    private val dao: FamilyDao
) {
    val all: Flow<List<FamilyMember>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun add(name: String, relation: Relation): Long =
        dao.insert(FamilyMemberEntity(name = name.trim(), relation = relation.name))

    suspend fun update(member: FamilyMember) = dao.update(
        FamilyMemberEntity(
            id = member.id,
            name = member.name,
            relation = member.relation.name,
            isSelf = member.isSelf,
            isArchived = member.isArchived
        )
    )

    suspend fun delete(id: Long) = dao.deleteById(id)
}
