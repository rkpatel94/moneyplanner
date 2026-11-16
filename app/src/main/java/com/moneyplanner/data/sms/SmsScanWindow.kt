package com.moneyplanner.data.sms

import java.time.Duration
import java.time.Instant

/** How far back a scan of the inbox reaches. */
enum class SmsScanPeriod(val label: String, val days: Long?) {
    SINCE_LAST_SCAN("Since last scan", null),
    LAST_7_DAYS("7 days", 7),
    LAST_30_DAYS("30 days", 30),
    LAST_90_DAYS("90 days", 90)
}

/**
 * Turns a chosen period into the moment a scan starts reading from.
 *
 * "Since last scan" is the everyday choice, because it is the one that only ever shows
 * what is new. A first scan has no last scan to start from, so it reads a month, which is
 * how far back most people would want to catch up and short enough to review in one go.
 */
object SmsScanWindow {

    const val FIRST_SCAN_DAYS = 30L

    /** The longest any scan reaches, including "since last scan" after a long gap. */
    const val LONGEST_DAYS = 90L

    /**
     * How far before the last scan a "since last scan" read starts again.
     *
     * A message is stamped with the moment it arrived but lands in the inbox a moment
     * later, so one arriving as a scan ran could otherwise fall between two scans. Reading
     * a few minutes twice costs nothing: anything already imported is recognised by its id.
     */
    val OVERLAP: Duration = Duration.ofMinutes(5)

    fun startFor(period: SmsScanPeriod, lastScanAt: Instant?, now: Instant): Instant {
        val longest = now.minus(Duration.ofDays(LONGEST_DAYS))
        val days = period.days
        if (days != null) return now.minus(Duration.ofDays(days))

        // A last scan in the future means the clock has been moved back since; trusting it
        // would read nothing at all, so it is treated like a first scan.
        if (lastScanAt == null || lastScanAt.isAfter(now)) {
            return now.minus(Duration.ofDays(FIRST_SCAN_DAYS))
        }
        val fromLastScan = lastScanAt.minus(OVERLAP)
        return if (fromLastScan.isBefore(longest)) longest else fromLastScan
    }
}
