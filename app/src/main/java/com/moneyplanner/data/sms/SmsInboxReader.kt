package com.moneyplanner.data.sms

import com.moneyplanner.domain.nlp.ParsedSms
import java.time.LocalDate

/**
 * A bank message the user has pasted in, and what the parser made of it.
 *
 * The app used to read these from the message inbox behind READ_SMS. That was removed:
 * Play grants the permission almost exclusively to default SMS handler apps, so it blocked
 * distribution for a feature that works without it, and an app trusted with this much
 * financial detail is stronger for never asking to read your messages at all.
 */
data class SmsCandidate(
    val smsId: Long,
    val sender: String,
    val receivedOn: LocalDate,
    val parsed: ParsedSms
)
