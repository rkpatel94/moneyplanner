package com.moneyplanner.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.moneyplanner.domain.nlp.BankSmsParser
import com.moneyplanner.domain.nlp.ParsedSms
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads bank alerts out of the SMS inbox.
 *
 * Messages are read, parsed and discarded in memory. Nothing about an SMS is stored: only
 * the transaction the user chooses to keep is written, and only after they have reviewed
 * it. The app never sends a message, never reads anything outside the inbox, and — since
 * it has no network permission at all — could not transmit a message even if it tried.
 *
 * Only senders that look like bank shortcodes are considered. Indian bank alerts arrive
 * from six-character IDs such as `AD-HDFCBK` or `VM-ICICIB`, never from a phone number,
 * so filtering on that shape keeps personal conversations out of the results entirely.
 */
@Singleton
class SmsInboxReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @com.moneyplanner.di.IoDispatcher private val io: CoroutineDispatcher
) {

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Parses recent bank alerts.
     *
     * @param sinceDays how far back to look. Kept short by default because the point is
     *        to catch up on recent spending, not to import a year of history at once.
     */
    suspend fun readRecent(sinceDays: Int = 30): SmsScanResult = withContext(io) {
        if (!hasPermission()) return@withContext SmsScanResult.PermissionMissing

        val cutoff = System.currentTimeMillis() - sinceDays.toLong() * 24 * 60 * 60 * 1000
        val results = mutableListOf<SmsCandidate>()
        var inspected = 0
        var fromBanks = 0

        val outcome = runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(cutoff.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    inspected++
                    val sender = cursor.getString(addressColumn).orEmpty()
                    if (!looksLikeBankSender(sender)) continue

                    val body = cursor.getString(bodyColumn).orEmpty()
                    if (body.isBlank()) continue
                    fromBanks++

                    val receivedOn = Instant.ofEpochMilli(cursor.getLong(dateColumn))
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()

                    val parsed = BankSmsParser.parse(body, receivedOn)
                    if (!parsed.isTransaction) continue

                    results += SmsCandidate(
                        smsId = cursor.getLong(idColumn),
                        sender = sender,
                        receivedOn = receivedOn,
                        parsed = parsed
                    )
                }
            }
        }

        outcome.fold(
            onSuccess = {
                SmsScanResult.Scanned(
                    candidates = results,
                    messagesInspected = inspected,
                    messagesFromBanks = fromBanks
                )
            },
            // A failure here is almost always the read being refused at the provider even
            // though the permission looks granted, which some manufacturer builds and
            // work profiles do. Reporting it matters: an empty list would be shown as
            // "nothing found", and the user would go looking for missing messages that
            // were never actually read.
            onFailure = { error -> SmsScanResult.Failed(error.message ?: error::class.java.simpleName) }
        )
    }

    /**
     * A bank alert comes from an alphanumeric shortcode, not a phone number. Anything
     * containing a digit-led number or a plus sign is a person, and is skipped.
     */
    private fun looksLikeBankSender(sender: String): Boolean {
        val cleaned = sender.trim()
        if (cleaned.isEmpty()) return false
        if (cleaned.startsWith("+")) return false
        if (cleaned.all { it.isDigit() }) return false
        return cleaned.any { it.isLetter() }
    }
}

/**
 * What a scan actually did.
 *
 * Distinguishing "read nothing" from "found nothing" is the point. An empty inbox, a
 * refused read and an inbox full of messages that all failed to parse look identical from
 * a bare list, and each needs a different thing from the user.
 */
sealed interface SmsScanResult {
    data object PermissionMissing : SmsScanResult
    data class Failed(val reason: String) : SmsScanResult
    data class Scanned(
        val candidates: List<SmsCandidate>,
        /** Every message in the window, before any filtering. */
        val messagesInspected: Int,
        /** Those that came from something shaped like a bank shortcode. */
        val messagesFromBanks: Int
    ) : SmsScanResult
}

data class SmsCandidate(
    val smsId: Long,
    val sender: String,
    val receivedOn: LocalDate,
    val parsed: ParsedSms
)
