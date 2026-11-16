package com.moneyplanner.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.moneyplanner.di.IoDispatcher
import com.moneyplanner.domain.nlp.BankSmsParser
import com.moneyplanner.domain.nlp.ParsedSms
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads bank alerts out of the SMS inbox, on the user's say-so.
 *
 * `READ_SMS` is asked for only when the user taps Scan on the import screen, and this
 * reader refuses to touch the inbox without it; nothing reads messages at start-up or in
 * the background. Pasting a message stays available for anyone who declines, and needs no
 * permission at all.
 *
 * Messages are read, parsed and discarded in memory. Nothing about an SMS is stored except
 * the id of one the user actually imported, so the same alert is not offered twice; the
 * transaction is written only after they have reviewed it. The app never sends a message,
 * reads nothing outside the inbox, and — with no network permission — could not pass a
 * message on even if it tried.
 *
 * Only senders shaped like a bank are considered (see [SmsSenderFilter]). Indian bank
 * alerts come from registered headers such as `VM-HDFCBK` or from short codes, never from
 * a phone number, so personal conversations are skipped before their text is looked at.
 */
@Singleton
class SmsInboxReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * The bank transactions received at or after [since], newest first.
     *
     * @param zone the zone a message's arrival time is read in, which decides the day it
     *        is dated when the text carries no date of its own.
     */
    suspend fun readSince(
        since: Instant,
        zone: ZoneId = ZoneId.systemDefault()
    ): SmsScanResult = withContext(io) {
        if (!hasPermission()) return@withContext SmsScanResult.PermissionMissing

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toEpochMilli().toString()),
                "${Telephony.Sms.DATE} DESC"
            ) ?: return@withContext SmsScanResult.Failed("The message inbox could not be opened.")

            val candidates = mutableListOf<SmsCandidate>()
            var inspected = 0
            var fromBanks = 0

            cursor.use { rows ->
                val idColumn = rows.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressColumn = rows.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyColumn = rows.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateColumn = rows.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (rows.moveToNext()) {
                    // A long inbox is worth abandoning promptly once nobody is waiting.
                    ensureActive()
                    inspected++

                    val sender = rows.getString(addressColumn)
                    if (!SmsSenderFilter.isLikelyBankSender(sender)) continue

                    val body = rows.getString(bodyColumn)
                    if (body.isNullOrBlank()) continue
                    fromBanks++

                    val receivedOn = Instant.ofEpochMilli(rows.getLong(dateColumn))
                        .atZone(zone)
                        .toLocalDate()

                    val parsed = BankSmsParser.parse(body, receivedOn)
                    if (!parsed.isTransaction) continue

                    candidates += SmsCandidate(
                        smsId = rows.getLong(idColumn),
                        sender = sender.orEmpty().trim(),
                        receivedOn = receivedOn,
                        parsed = parsed,
                        source = SmsSource.INBOX
                    )
                }
            }

            SmsScanResult.Scanned(
                candidates = candidates,
                messagesInspected = inspected,
                messagesFromBanks = fromBanks
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: SecurityException) {
            // Some manufacturer builds and work profiles refuse the read at the provider
            // even though the permission shows as granted. Saying so matters: an empty
            // list would read as "nothing found", and the user would go looking for
            // messages that were never actually read.
            SmsScanResult.Failed("The phone refused to let the app read messages.")
        } catch (failure: RuntimeException) {
            // The provider's own exception text is written for developers, not for the
            // person holding the phone, so it is not passed on.
            SmsScanResult.Failed("The phone's message store gave an error.")
        }
    }
}

/**
 * What a scan actually did.
 *
 * Distinguishing "read nothing" from "found nothing" is the point. A refused read, an inbox
 * with no bank messages and one full of alerts that were not transactions look identical
 * as a bare list, and each needs something different from the user.
 */
sealed interface SmsScanResult {
    data object PermissionMissing : SmsScanResult
    data class Failed(val reason: String) : SmsScanResult
    data class Scanned(
        val candidates: List<SmsCandidate>,
        /** Every message in the window, before any filtering. */
        val messagesInspected: Int,
        /** Those from a sender shaped like a bank. */
        val messagesFromBanks: Int
    ) : SmsScanResult
}

/** Where a candidate came from: the inbox, or text the user pasted in. */
enum class SmsSource { INBOX, PASTED }

/**
 * A bank message and what the parser made of it.
 *
 * [smsId] is the inbox's own id for a scanned message, which is what lets a later scan
 * recognise it. Pasted text has no such id, so it is given a negative one that can never
 * collide with a real message.
 */
data class SmsCandidate(
    val smsId: Long,
    val sender: String,
    val receivedOn: LocalDate,
    val parsed: ParsedSms,
    val source: SmsSource = SmsSource.INBOX
) {
    val isFromInbox: Boolean get() = source == SmsSource.INBOX
}
