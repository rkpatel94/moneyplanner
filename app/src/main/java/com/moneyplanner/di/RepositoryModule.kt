package com.moneyplanner.di

import com.moneyplanner.data.repo.SystemTodayProvider
import com.moneyplanner.data.repo.TodayProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Bound rather than constructed directly so that date-dependent behaviour can be
     * pinned in tests without waiting for a real calendar day to roll over.
     */
    @Binds
    @Singleton
    abstract fun bindTodayProvider(impl: SystemTodayProvider): TodayProvider
}
