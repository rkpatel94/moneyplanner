package com.moneyplanner.ui.nav

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which month a bottom-bar tab should open on, when a link rather than the bar opens it.
 *
 * Returning to a tab restores whatever the user last paged to there. That is right for a
 * tap on the bar, where somebody is going back to what they were looking at, and wrong for
 * a link: "View all" under this week's payments, an overdue row, or "See what is dated that
 * month" under a December shortfall each name a month, and opening on a different one shows
 * none of what was promised.
 *
 * A navigation argument cannot carry the request, because restoring a saved tab brings back
 * the arguments it was first opened with. A request left here is picked up by the tab's
 * view model whether that view model is being created or restored, and is used once.
 */
@Singleton
class TabFocus @Inject constructor() {

    /** A month for the calendar, or null for the month today is in. */
    val calendar = FocusRequests<YearMonth?>()

    /** The forecast back at the month in progress. */
    val forecast = FocusRequests<Unit>()

    /** A month for reports, or null for the month today is in. */
    val reports = FocusRequests<YearMonth?>()
}

/**
 * One pending request, handed to the next collector and then cleared.
 *
 * Each request is its own object, so asking for the same month twice in a row still
 * arrives twice: a state flow would otherwise treat the second as no change at all.
 */
class FocusRequests<T> {

    private class Request<T>(val value: T)

    private val pending = MutableStateFlow<Request<T>?>(null)

    fun request(value: T) {
        pending.value = Request(value)
    }

    /** Every request, once each, for the one view model that serves this tab. */
    val requests: Flow<T> = pending
        .filterNotNull()
        .map { request ->
            pending.compareAndSet(request, null)
            request.value
        }
}

/** For the navigation graph, which is composed outside any view model. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TabFocusEntryPoint {
    fun tabFocus(): TabFocus
}
